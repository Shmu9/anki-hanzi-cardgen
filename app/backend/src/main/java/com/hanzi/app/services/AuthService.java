package com.hanzi.app.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class AuthService {
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int SESSION_TOKEN_BYTES = 32;
    private static final Duration SESSION_TTL = Duration.ofMinutes(20);
    private static final Duration ABSOLUTE_SESSION_TTL = Duration.ofHours(12);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,20}$");
    private static final Pattern PASSWORD_NUMBER_OR_SYMBOL = Pattern.compile("[^A-Za-z]");
    private static final Set<String> RESERVED_DISPLAY_NAMES = Set.of("admin", "administrator", "support", "system", "root", "staff");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public AuthService(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = normalizeJdbcUrl(dbUrl);
        this.dbUser = blankToNull(dbUser);
        this.dbPassword = blankToNull(dbPassword);
        if (isConfigured()) {
            initializeSchema();
        }
    }

    public boolean isConfigured() {
        return dbUrl != null;
    }

    public Map<String, Object> register(Map<String, Object> request, String userAgent, String ipAddress)
            throws AuthException, SQLException {
        requireConfigured();
        String email = normalizeEmail(stringValue(request, "email"));
        String username = normalizeUsername(stringValue(request, "username"));
        String displayName = normalizeDisplayName(stringValue(request, "displayName"));
        String password = stringValue(request, "password");

        if (email == null) {
            throw new AuthException(400, "Email is required.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new AuthException(400, "Enter a valid email address.");
        }
        validateDisplayName(displayName);
        validatePassword(password, email, displayName);

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> user = insertUser(conn, email, username, displayName, hashPassword(password));
                Map<String, Object> session = createSession(conn, UUID.fromString(user.get("id").toString()), userAgent, ipAddress);
                conn.commit();
                return authPayload(user, session);
            } catch (SQLException ex) {
                conn.rollback();
                if ("23505".equals(ex.getSQLState())) {
                    throw new AuthException(409, "An account with that email or display name already exists.");
                }
                throw ex;
            }
        }
    }

    public Map<String, Object> signIn(Map<String, Object> request, String userAgent, String ipAddress)
            throws AuthException, SQLException {
        requireConfigured();
        String identifier = blankToNull(stringValue(request, "identifier"));
        String password = stringValue(request, "password");
        if (identifier == null || password == null || password.isBlank()) {
            throw new AuthException(400, "Email or username and password are required.");
        }

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                StoredUser stored = findUserForSignIn(conn, identifier);
                if (stored == null || !verifyPassword(password, stored.passwordHash())) {
                    throw new AuthException(401, "Invalid email or password.");
                }
                if (!"active".equals(stored.user().get("status"))) {
                    throw new AuthException(403, "This account is not active.");
                }
                touchUser(conn, stored.id());
                Map<String, Object> user = findUserById(conn, stored.id());
                Map<String, Object> session = createSession(conn, stored.id(), userAgent, ipAddress);
                conn.commit();
                return authPayload(user, session);
            } catch (AuthException | SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public Map<String, Object> session(String token) throws AuthException, SQLException {
        requireConfigured();
        if (blankToNull(token) == null) {
            return Map.of("authenticated", false);
        }

        try (Connection conn = connect()) {
            SessionUser sessionUser = findSession(conn, token);
            if (sessionUser == null) {
                return Map.of("authenticated", false);
            }
            touchUser(conn, sessionUser.userId());
            Map<String, Object> session = refreshSession(conn, token, sessionUser.sessionId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("authenticated", true);
            payload.put("user", findUserById(conn, sessionUser.userId()));
            payload.put("session", session);
            return payload;
        }
    }

    public Map<String, Object> signOut(String token) throws AuthException, SQLException {
        requireConfigured();
        if (blankToNull(token) != null) {
            try (Connection conn = connect();
                    PreparedStatement statement = conn.prepareStatement("""
                            UPDATE user_sessions
                            SET revoked_at = now()
                            WHERE token_hash = ?
                              AND revoked_at IS NULL
                            """)) {
                statement.setString(1, hashToken(token));
                statement.executeUpdate();
            }
        }
        return Map.of("authenticated", false);
    }

    private void initializeSchema() {
        try (InputStream input = AuthService.class.getResourceAsStream("/db/app.sql")) {
            if (input == null) {
                throw new IllegalStateException("Missing /db/app.sql resource.");
            }
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conn = connect(); Statement statement = conn.createStatement()) {
                statement.execute(schema);
            }
        } catch (IOException | SQLException ex) {
            throw new IllegalStateException("Could not initialize application auth schema.", ex);
        }
    }

    private Connection connect() throws SQLException {
        loadPostgresDriver();
        if (dbUser == null) {
            return DriverManager.getConnection(dbUrl);
        }
        Properties props = new Properties();
        props.setProperty("user", dbUser);
        if (dbPassword != null) {
            props.setProperty("password", dbPassword);
        }
        return DriverManager.getConnection(dbUrl, props);
    }

    private static void loadPostgresDriver() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("PostgreSQL JDBC driver is not available.", ex);
        }
    }

    private Map<String, Object> insertUser(
            Connection conn, String email, String username, String displayName, String passwordHash) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                INSERT INTO users (email, username, display_name, password_hash, status, last_active_at)
                VALUES (?, ?, ?, ?, 'active', now())
                RETURNING id, email, username, display_name, status, created_at, last_active_at
                """)) {
            statement.setString(1, email);
            statement.setString(2, username);
            statement.setString(3, displayName);
            statement.setString(4, passwordHash);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return userMap(rows);
            }
        }
    }

    private StoredUser findUserForSignIn(Connection conn, String identifier) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT id, email, username, display_name, status, password_hash, created_at, last_active_at
                FROM users
                WHERE lower(email) = lower(?)
                   OR lower(username) = lower(?)
                   OR lower(display_name) = lower(?)
                LIMIT 1
                """)) {
            statement.setString(1, identifier);
            statement.setString(2, identifier);
            statement.setString(3, identifier);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new StoredUser((UUID) rows.getObject("id"), rows.getString("password_hash"), userMap(rows));
            }
        }
    }

    private Map<String, Object> findUserById(Connection conn, UUID userId) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT id, email, username, display_name, status, created_at, last_active_at
                FROM users
                WHERE id = ?
                """)) {
            statement.setObject(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return userMap(rows);
            }
        }
    }

    private Map<String, Object> createSession(Connection conn, UUID userId, String userAgent, String ipAddress)
            throws SQLException {
        String token = sessionToken();
        try (PreparedStatement statement = conn.prepareStatement("""
                INSERT INTO user_sessions (user_id, token_hash, user_agent, ip_address, expires_at, absolute_expires_at)
                VALUES (
                    ?,
                    ?,
                    ?,
                    NULLIF(?, '')::inet,
                    now() + (? * interval '1 second'),
                    now() + (? * interval '1 second')
                )
                RETURNING id, user_id, created_at, expires_at, absolute_expires_at
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, hashToken(token));
            statement.setString(3, truncate(userAgent, 512));
            statement.setString(4, blankToNull(ipAddress));
            statement.setLong(5, SESSION_TTL.toSeconds());
            statement.setLong(6, ABSOLUTE_SESSION_TTL.toSeconds());
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                Map<String, Object> session = sessionMap(rows);
                session.put("token", token);
                return session;
            }
        }
    }

    private SessionUser findSession(Connection conn, String token) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT sessions.id, sessions.user_id, sessions.created_at, sessions.expires_at, sessions.absolute_expires_at
                FROM user_sessions AS sessions
                JOIN users ON users.id = sessions.user_id
                WHERE sessions.token_hash = ?
                  AND sessions.revoked_at IS NULL
                  AND sessions.expires_at > now()
                  AND sessions.absolute_expires_at > now()
                  AND users.status = 'active'
                LIMIT 1
                """)) {
            statement.setString(1, hashToken(token));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                UUID userId = (UUID) rows.getObject("user_id");
                return new SessionUser((UUID) rows.getObject("id"), userId, sessionMap(rows));
            }
        }
    }

    private Map<String, Object> refreshSession(Connection conn, String token, UUID sessionId) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                UPDATE user_sessions
                SET expires_at = LEAST(now() + (? * interval '1 second'), absolute_expires_at)
                WHERE id = ?
                  AND token_hash = ?
                  AND revoked_at IS NULL
                  AND expires_at > now()
                  AND absolute_expires_at > now()
                RETURNING id, user_id, created_at, expires_at, absolute_expires_at
                """)) {
            statement.setLong(1, SESSION_TTL.toSeconds());
            statement.setObject(2, sessionId);
            statement.setString(3, hashToken(token));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return sessionMap(rows);
            }
        }
    }

    private void touchUser(Connection conn, UUID userId) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                UPDATE users
                SET last_active_at = now(),
                    updated_at = now()
                WHERE id = ?
                """)) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    private Map<String, Object> authPayload(Map<String, Object> user, Map<String, Object> session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authenticated", true);
        payload.put("user", user);
        payload.put("session", withoutToken(session));
        payload.put("token", session.get("token"));
        return payload;
    }

    private Map<String, Object> withoutToken(Map<String, Object> session) {
        Map<String, Object> safe = new LinkedHashMap<>(session);
        safe.remove("token");
        return safe;
    }

    private Map<String, Object> userMap(ResultSet rows) throws SQLException {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", rows.getString("id"));
        user.put("email", rows.getString("email"));
        user.put("username", rows.getString("username"));
        user.put("displayName", rows.getString("display_name"));
        user.put("status", rows.getString("status"));
        user.put("createdAt", objectString(rows, "created_at"));
        user.put("lastActiveAt", objectString(rows, "last_active_at"));
        return user;
    }

    private Map<String, Object> sessionMap(ResultSet rows) throws SQLException {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", rows.getString("id"));
        session.put("userId", rows.getString("user_id"));
        session.put("createdAt", objectString(rows, "created_at"));
        session.put("expiresAt", objectString(rows, "expires_at"));
        session.put("absoluteExpiresAt", objectString(rows, "absolute_expires_at"));
        return session;
    }

    private static String objectString(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        return value == null ? null : value.toString();
    }

    private static void validateDisplayName(String displayName) throws AuthException {
        if (displayName == null) {
            throw new AuthException(400, "Display name is required.");
        }
        if (!isValidDisplayName(displayName)) {
            throw new AuthException(400, "Display name must be 3 to 20 characters and use only letters, numbers, underscores, or hyphens.");
        }
    }

    private static boolean isValidDisplayName(String displayName) {
        if (displayName == null || !DISPLAY_NAME_PATTERN.matcher(displayName).matches()) {
            return false;
        }
        if (RESERVED_DISPLAY_NAMES.contains(displayName.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return true;
    }

    private static void validatePassword(String password, String email, String displayName) throws AuthException {
        if (password == null || password.length() < 8) {
            throw new AuthException(400, "Password must be at least 8 characters.");
        }
        if (!PASSWORD_NUMBER_OR_SYMBOL.matcher(password).find()) {
            throw new AuthException(400, "Password must include at least one number or symbol.");
        }
        String passwordLower = password.toLowerCase(Locale.ROOT);
        if (displayName != null && passwordLower.contains(displayName.toLowerCase(Locale.ROOT))) {
            throw new AuthException(400, "Password must not include your display name.");
        }
        if (containsEmailSubstring(passwordLower, email)) {
            throw new AuthException(400, "Password must not include long pieces of your email address.");
        }
    }

    private static boolean containsEmailSubstring(String passwordLower, String email) {
        if (email == null) {
            return false;
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        for (int start = 0; start <= normalizedEmail.length() - 5; start++) {
            for (int end = start + 5; end <= normalizedEmail.length(); end++) {
                if (passwordLower.contains(normalizedEmail.substring(start, end))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String hashPassword(String password) throws AuthException {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] hash = pbkdf2(password.toCharArray(), salt, PBKDF2_ITERATIONS);
        return "pbkdf2_sha256$" + PBKDF2_ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    private static boolean verifyPassword(String password, String storedHash) throws AuthException {
        if (storedHash == null || !storedHash.startsWith("pbkdf2_sha256$")) {
            return false;
        }
        String[] parts = storedHash.split("\\$", 4);
        if (parts.length != 4) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = pbkdf2(password.toCharArray(), salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws AuthException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password, salt, iterations, PBKDF2_KEY_BITS);
            return factory.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new AuthException(500, "Password hashing is unavailable.");
        }
    }

    private static String sessionToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(SESSION_TOKEN_BYTES));
    }

    private static String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private void requireConfigured() throws AuthException {
        if (!isConfigured()) {
            throw new AuthException(503, "Authentication database is not configured.");
        }
    }

    private static String stringValue(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? null : value.toString();
    }

    private static String normalizeEmail(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private static String normalizeUsername(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private static String normalizeDisplayName(String value) {
        return blankToNull(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        String normalized = blankToNull(value);
        return normalized == null || normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String normalizeJdbcUrl(String value) {
        String normalized = blankToNull(value);
        if (normalized == null || normalized.startsWith("jdbc:")) {
            return normalized;
        }
        if (normalized.startsWith("postgres://")) {
            return "jdbc:postgresql://" + normalized.substring("postgres://".length());
        }
        if (normalized.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + normalized.substring("postgresql://".length());
        }
        return normalized;
    }

    private record StoredUser(UUID id, String passwordHash, Map<String, Object> user) {}

    private record SessionUser(UUID sessionId, UUID userId, Map<String, Object> session) {}

    public static final class AuthException extends Exception {
        private final int status;

        public AuthException(int status, String message) {
            super(Objects.requireNonNull(message));
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
