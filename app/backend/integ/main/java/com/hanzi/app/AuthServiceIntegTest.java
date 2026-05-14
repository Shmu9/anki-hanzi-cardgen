package com.hanzi.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanzi.app.services.AnkiSyncService;
import com.hanzi.app.services.AuthService;
import com.hanzi.app.services.AuthService.AuthSettings;
import com.hanzi.app.services.DictionaryService;
import com.hanzi.app.services.FlashcardService;
import com.hanzi.app.services.MnemonicService;
import com.hanzi.app.services.PreferenceService;
import com.hanzi.app.utils.HttpHelper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuthServiceIntegTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};
    private static final AppConfig DEFAULT_APP_CONFIG = AppConfig.parse(new String[0]);

    private HttpServer server;
    private URI baseUri;
    private AuthService auth;
    private final List<String> createdUserIds = new ArrayList<>();

    @BeforeEach
    public void startServer() throws IOException {
        assumeTrue(dbUrl() != null,
                "Start local PostgreSQL or set HANZI_INTEG_DB_URL/the backend app DB config to run integration tests.");

        auth = new AuthService(
                dbUrl(),
                firstEnv("HANZI_INTEG_DB_USER", "HANZI_APP_DB_USER", "PGUSER"),
                firstEnv("HANZI_INTEG_DB_PASSWORD", "HANZI_APP_DB_PASSWORD", "PGPASSWORD"),
                new AuthSettings(Duration.ofSeconds(2), Duration.ofSeconds(4)));

        ApiRoutes routes = new ApiRoutes(
                new DictionaryService(new GlyphStore(DEFAULT_APP_CONFIG.dbPath())),
                new FlashcardService(),
                new MnemonicService(),
                new AnkiSyncService(),
                new PreferenceService(
                        dbUrl(),
                        firstEnv("HANZI_INTEG_DB_USER", "HANZI_APP_DB_USER", "PGUSER"),
                        firstEnv("HANZI_INTEG_DB_PASSWORD", "HANZI_APP_DB_PASSWORD", "PGPASSWORD")),
                auth);

        server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                String rawPath = exchange.getRequestURI().getRawPath();
                String path = normalizePath(HttpHelper.decodePath(rawPath));
                routes.handle(exchange, path, normalizePath(rawPath), HttpHelper.queryParams(exchange.getRequestURI()));
            } catch (Exception ex) {
                HttpHelper.sendJson(exchange, 500, Map.of("error", ex.getMessage()));
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    public void tearDown() throws Exception {
        cleanupCreatedUsers();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void preferencesRequireAuthentication() throws Exception {
        JsonResponse response = getPreferences(null);

        assertEquals(401, response.status());
        assertEquals("Authentication required.", response.body().get("error"));
    }

    @Test
    public void activeSessionCanViewPreferences() throws Exception {
        RegisteredUser registered = registerUniqueUser();

        JsonResponse response = getPreferences(registered.token());

        assertEquals(200, response.status());
        assertEquals(true, response.body().get("implemented"));
        assertEquals(registered.userId(), response.body().get("userId"));
    }

    @Test
    public void activeSessionCanSaveAndRankComponentMeanings() throws Exception {
        RegisteredUser registered = registerUniqueUser();

        JsonResponse first = postJson("/api/preferences/radicals",
                Map.of("glyph", "良", "meaning", "good, fine"), registered.token());
        assertEquals(201, first.status());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstMeanings = (List<Map<String, Object>>) first.body().get("componentMeanings");
        assertEquals(1, firstMeanings.size());
        assertEquals("良", firstMeanings.getFirst().get("componentGlyph"));
        assertEquals("good, fine", firstMeanings.getFirst().get("meaning"));
        assertEquals(true, firstMeanings.getFirst().get("isPrimary"));

        JsonResponse second = postJson("/api/preferences/radicals",
                Map.of("glyph", "良", "meaning", "wholesome"), registered.token());
        assertEquals(201, second.status());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secondMeanings = (List<Map<String, Object>>) second.body().get("componentMeanings");
        assertEquals(2, secondMeanings.size());

        JsonResponse bulkSaved = putJson("/api/preferences/radicals/%E8%89%AF", Map.of(
                "useStandardDefinitionInMnemonics", true,
                "definitions", List.of(
                        Map.of("id", secondMeanings.get(1).get("id").toString(), "meaning", "wholesome", "useInMnemonics", true),
                        Map.of("id", secondMeanings.get(0).get("id").toString(), "meaning", "good, fine", "useInMnemonics", false)
                )), registered.token());
        assertEquals(200, bulkSaved.status());
        assertEquals(true, bulkSaved.body().get("useStandardDefinitionInMnemonics"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bulkMeanings = (List<Map<String, Object>>) bulkSaved.body().get("componentMeanings");
        assertEquals("wholesome", bulkMeanings.getFirst().get("meaning"));
        assertEquals(true, bulkMeanings.getFirst().get("useInMnemonics"));

        List<String> reversedIds = List.of(bulkMeanings.get(0).get("id").toString(), bulkMeanings.get(1).get("id").toString());
        JsonResponse ranked = putJson("/api/preferences/radicals/%E8%89%AF", Map.of("ids", reversedIds), registered.token());

        assertEquals(200, ranked.status());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rankedMeanings = (List<Map<String, Object>>) ranked.body().get("componentMeanings");
        assertEquals("wholesome", rankedMeanings.getFirst().get("meaning"));
        assertEquals(0, rankedMeanings.getFirst().get("rank"));
        assertEquals(true, rankedMeanings.getFirst().get("isPrimary"));

        String firstId = rankedMeanings.getFirst().get("id").toString();
        JsonResponse updated = patchJson("/api/preferences/radicals/%E8%89%AF/" + firstId,
                Map.of("meaning", "good and wholesome", "useInMnemonics", true), registered.token());

        assertEquals(200, updated.status());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updatedMeanings = (List<Map<String, Object>>) updated.body().get("componentMeanings");
        assertEquals("good and wholesome", updatedMeanings.getFirst().get("meaning"));
        assertEquals(true, updatedMeanings.getFirst().get("useInMnemonics"));

        JsonResponse deleted = deleteJson("/api/preferences/radicals/%E8%89%AF/" + firstId, registered.token());

        assertEquals(200, deleted.status());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remainingMeanings = (List<Map<String, Object>>) deleted.body().get("componentMeanings");
        assertEquals(1, remainingMeanings.size());
    }

    @Test
    public void preferencesRejectIdleExpiredSessionWithoutWaitingTwentyMinutes() throws Exception {
        RegisteredUser registered = registerUniqueUser();
        expireIdleSession(registered.userId());

        JsonResponse response = getPreferences(registered.token());

        assertEquals(401, response.status());
        assertEquals("Session expired. Sign in again to continue.", response.body().get("error"));
    }

    @Test
    public void preferencesRejectAbsoluteExpiredSessionEvenWhenIdleExpiryIsFresh() throws Exception {
        RegisteredUser registered = registerUniqueUser();
        expireAbsoluteSessionOnly(registered.userId());

        JsonResponse response = getPreferences(registered.token());

        assertEquals(401, response.status());
        assertEquals("Session expired. Sign in again to continue.", response.body().get("error"));
    }

    @Test
    public void searchIsAccessibleWithoutAuthentication() throws Exception {
        JsonResponse response = getJson("/api/search?q=%E6%BC%A2&limit=5", null);

        assertEquals(200, response.status());
        // TODO: avoid unchecked casts in tests by adding typed response objects or helper methods
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.body().get("results");
        assertFalse(results.isEmpty());
        assertEquals("漢", results.getFirst().get("glyph"));
    }

    @Test
    public void characterDetailIsAccessibleWithoutAuthentication() throws Exception {
        JsonResponse response = getJson("/api/glyph/%E6%BC%A2", null);

        assertEquals(200, response.status());
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) response.body().get("entry");
        assertEquals("漢", entry.get("glyph"));
    }

    private RegisteredUser registerUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toLowerCase(Locale.ROOT);
        Map<String, String> payload = Map.of(
                "email", "learner-" + suffix + "@example.test",
                "displayName", "learner_" + suffix.substring(0, 8),
                "password", "Studytime9!");
        JsonResponse response = postJson("/api/auth/register", payload, null);
        assertEquals(201, response.status());
        assertTrue(response.body().containsKey("token"));
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.body().get("user");
        String userId = user.get("id").toString();
        createdUserIds.add(userId);
        return new RegisteredUser(userId, response.body().get("token").toString());
    }

    private JsonResponse getPreferences(String token) throws Exception {
        return getJson("/api/preferences", token);
    }

    private JsonResponse getJson(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.build());
    }

    private JsonResponse postJson(String path, Object payload, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.build());
    }

    private JsonResponse putJson(String path, Object payload, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.build());
    }

    private JsonResponse patchJson(String path, Object payload, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.build());
    }

    private JsonResponse deleteJson(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path)).DELETE();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.build());
    }

    private JsonResponse send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return new JsonResponse(response.statusCode(), MAPPER.readValue(response.body(), JSON_OBJECT));
    }

    private void expireIdleSession(String userId) throws Exception {
        updateSession("""
                UPDATE user_sessions
                SET expires_at = now() - interval '1 second',
                    absolute_expires_at = now() + interval '1 hour'
                WHERE user_id = ?::uuid
                  AND revoked_at IS NULL
                """, userId);
    }

    private void expireAbsoluteSessionOnly(String userId) throws Exception {
        updateSession("""
                UPDATE user_sessions
                SET expires_at = now() + interval '1 hour',
                    absolute_expires_at = now() - interval '1 second'
                WHERE user_id = ?::uuid
                  AND revoked_at IS NULL
                """, userId);
    }

    private void updateSession(String sql, String userId) throws Exception {
        try (Connection conn = connect();
                PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, userId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void cleanupCreatedUsers() throws Exception {
        if (createdUserIds.isEmpty() || dbUrl() == null) {
            return;
        }
        try (Connection conn = connect()) {
            for (String userId : createdUserIds) {
                try (PreparedStatement sessions = conn.prepareStatement(
                        "DELETE FROM user_sessions WHERE user_id = ?::uuid")) {
                    sessions.setString(1, userId);
                    sessions.executeUpdate();
                }
                try (PreparedStatement users = conn.prepareStatement(
                        "DELETE FROM users WHERE id = ?::uuid")) {
                    users.setString(1, userId);
                    users.executeUpdate();
                }
            }
        } finally {
            createdUserIds.clear();
        }
    }

    private Connection connect() throws Exception {
        String user = dbPropsUser();
        if (user == null) {
            return DriverManager.getConnection(dbUrl());
        }
        Properties properties = new Properties();
        properties.setProperty("user", user);
        String password = dbPropsPassword();
        if (password != null) {
            properties.setProperty("password", password);
        }
        return DriverManager.getConnection(dbUrl(), properties);
    }

    private static String dbUrl() {
        String integrationUrl = firstEnv("HANZI_INTEG_DB_URL");
        if (integrationUrl != null) {
            return normalizeJdbcUrl(integrationUrl);
        }
        if (DEFAULT_APP_CONFIG.appDbUrl() != null) {
            return normalizeJdbcUrl(DEFAULT_APP_CONFIG.appDbUrl());
        }
        return "jdbc:postgresql://%s:%s/%s".formatted(
                firstEnvOrDefault("POSTGRES_HOST", AppConfig.DEFAULT_POSTGRES_HOST),
                firstEnvOrDefault("POSTGRES_PORT", AppConfig.DEFAULT_POSTGRES_PORT),
                firstEnvOrDefault("APP_DB_NAME", AppConfig.DEFAULT_APP_DB_NAME));
    }

    private static String dbPropsUser() {
        String integrationUser = firstEnv("HANZI_INTEG_DB_USER");
        if (integrationUser != null) {
            return integrationUser;
        }
        if (DEFAULT_APP_CONFIG.appDbUser() != null) {
            return DEFAULT_APP_CONFIG.appDbUser();
        }
        return firstEnv("USER");
    }

    private static String dbPropsPassword() {
        String integrationPassword = firstEnv("HANZI_INTEG_DB_PASSWORD");
        return integrationPassword == null ? DEFAULT_APP_CONFIG.appDbPassword() : integrationPassword;
    }

    private static String firstEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String firstEnvOrDefault(String name, String fallback) {
        String value = firstEnv(name);
        return value == null ? fallback : value;
    }

    private static String normalizeJdbcUrl(String value) {
        if (value == null || value.isBlank() || value.startsWith("jdbc:")) {
            return value;
        }
        if (value.startsWith("postgres://")) {
            return "jdbc:postgresql://" + value.substring("postgres://".length());
        }
        if (value.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + value.substring("postgresql://".length());
        }
        return value;
    }

    private static String normalizePath(String path) {
        return path == null ? "/" : path.replaceAll("/{2,}", "/");
    }

    private record JsonResponse(int status, Map<String, Object> body) {}

    private record RegisteredUser(String userId, String token) {}
}
