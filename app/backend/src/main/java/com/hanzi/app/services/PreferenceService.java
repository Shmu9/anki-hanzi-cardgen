package com.hanzi.app.services;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class PreferenceService extends StubService {
    private static final int MAX_MEANINGS_PER_COMPONENT = 5;
    private static final int MAX_MEANING_LENGTH = 50;

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public PreferenceService() {
        this(null, null, null);
    }

    public PreferenceService(String dbUrl, String dbUser, String dbPassword) {
        super("user preferences", List.of(
                "GET /api/preferences",
                "PUT /api/preferences",
                "GET /api/preferences/radicals",
                "POST /api/preferences/radicals",
                "PUT /api/preferences/radicals/{component}",
                "PATCH /api/preferences/radicals/{component}/{preferenceId}",
                "DELETE /api/preferences/radicals/{component}/{preferenceId}"));
        this.dbUrl = normalizeJdbcUrl(dbUrl);
        this.dbUser = blankToNull(dbUser);
        this.dbPassword = blankToNull(dbPassword);
    }

    public Map<String, Object> overview(AuthService.SessionContext sessionContext) throws SQLException, PreferenceException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("implemented", true);
        payload.put("userId", sessionContext.userId().toString());
        payload.put("user", sessionContext.user());
        payload.put("session", sessionContext.session());
        payload.put("mnemonicProfile", Map.of(
                "name", "Scene-based learner",
                "structure", "Scene Story",
                "toneHints", "When helpful"));
        payload.put("componentMeanings", componentMeanings(sessionContext, null));
        payload.put("standardDefinitionPreferences", standardDefinitionPreferences(sessionContext, null));
        return payload;
    }

    public Map<String, Object> componentMeaningsPayload(AuthService.SessionContext sessionContext, String glyph)
            throws SQLException, PreferenceException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("componentMeanings", componentMeanings(sessionContext, glyph));
        String normalizedGlyph = blankToNull(glyph);
        if (normalizedGlyph != null) {
            payload.put("useStandardDefinitionInMnemonics", useStandardDefinitionInMnemonics(sessionContext, normalizedGlyph));
        }
        return payload;
    }

    public Map<String, Object> saveComponentMeaning(AuthService.SessionContext sessionContext, Map<String, Object> request)
            throws SQLException, PreferenceException {
        requireConfigured();
        String glyph = normalizeRequiredString(request, "glyph", "Component glyph is required.");
        String meaning = normalizeRequiredString(request, "meaning", "Preferred meaning is required.");
        requireMeaningLength(meaning);
        String token = blankToNull(stringValue(request, "componentToken"));
        String notes = blankToNull(stringValue(request, "notes"));
        int requestedRank = optionalRank(request.get("rank"));
        boolean isPrimary = booleanValue(request.get("isPrimary"), false);
        boolean useInMnemonics = booleanValue(request.get("useInMnemonics"), false);

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                int count = countMeanings(conn, sessionContext.userId(), glyph);
                Map<String, Object> existing = findMeaning(conn, sessionContext.userId(), glyph, meaning);
                if (existing == null && count >= MAX_MEANINGS_PER_COMPONENT) {
                    throw new PreferenceException(409, "You can save up to five preferred meanings per glyph.");
                }
                if (useInMnemonics) {
                    requireMnemonicUsageCapacity(
                            conn,
                            sessionContext.userId(),
                            glyph,
                            existing == null ? null : existing.get("id").toString());
                }
                int rank = existing == null ? nextRank(conn, sessionContext.userId(), glyph, requestedRank) : numberValue(existing.get("rank"));
                if (isPrimary) {
                    clearPrimary(conn, sessionContext.userId(), glyph);
                }
                Map<String, Object> preference = upsertMeaning(
                        conn, sessionContext.userId(), glyph, token, meaning, rank, isPrimary, useInMnemonics, notes);
                if (Boolean.TRUE.equals(preference.get("isPrimary"))) {
                    conn.commit();
                    return componentMeaningsPayload(sessionContext, glyph);
                }
                ensurePrimary(conn, sessionContext.userId(), glyph);
                conn.commit();
                return componentMeaningsPayload(sessionContext, glyph);
            } catch (PreferenceException | SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public Map<String, Object> updateComponentMeaning(
            AuthService.SessionContext sessionContext, String glyph, String preferenceId, Map<String, Object> request)
            throws SQLException, PreferenceException {
        requireConfigured();
        String componentGlyph = requirePathGlyph(glyph);
        String id = requirePreferenceId(preferenceId);

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> existing = findMeaningById(conn, sessionContext.userId(), componentGlyph, id);
                if (existing == null) {
                    throw new PreferenceException(404, "Preferred meaning not found.");
                }
                String meaning = request.containsKey("meaning")
                        ? normalizeRequiredString(request, "meaning", "Preferred meaning is required.")
                        : existing.get("meaning").toString();
                requireMeaningLength(meaning);
                String notes = request.containsKey("notes") ? blankToNull(stringValue(request, "notes")) : Objects.toString(existing.get("notes"), null);
                boolean useInMnemonics = request.containsKey("useInMnemonics")
                        ? booleanValue(request.get("useInMnemonics"), false)
                        : Boolean.TRUE.equals(existing.get("useInMnemonics"));
                if (useInMnemonics) {
                    requireMnemonicUsageCapacity(conn, sessionContext.userId(), componentGlyph, id);
                }
                updateMeaning(conn, sessionContext.userId(), componentGlyph, id, meaning, notes, useInMnemonics);
                conn.commit();
                return componentMeaningsPayload(sessionContext, componentGlyph);
            } catch (PreferenceException | SQLException ex) {
                conn.rollback();
                if (ex instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                    throw new PreferenceException(409, "That preferred meaning is already saved for this glyph.");
                }
                throw ex;
            }
        }
    }

    public Map<String, Object> deleteComponentMeaning(
            AuthService.SessionContext sessionContext, String glyph, String preferenceId)
            throws SQLException, PreferenceException {
        requireConfigured();
        String componentGlyph = requirePathGlyph(glyph);
        String id = requirePreferenceId(preferenceId);

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                int deleted = deleteMeaning(conn, sessionContext.userId(), componentGlyph, id);
                if (deleted == 0) {
                    throw new PreferenceException(404, "Preferred meaning not found.");
                }
                normalizeRanks(conn, sessionContext.userId(), componentGlyph);
                ensurePrimary(conn, sessionContext.userId(), componentGlyph);
                conn.commit();
                return componentMeaningsPayload(sessionContext, componentGlyph);
            } catch (PreferenceException | SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public Map<String, Object> rankComponentMeanings(
            AuthService.SessionContext sessionContext, String glyph, Map<String, Object> request)
            throws SQLException, PreferenceException {
        requireConfigured();
        String componentGlyph = requirePathGlyph(glyph);
        List<String> orderedIds = orderedIds(request);
        if (orderedIds.isEmpty()) {
            throw new PreferenceException(400, "Ranked preference IDs are required.");
        }
        if (orderedIds.size() > MAX_MEANINGS_PER_COMPONENT) {
            throw new PreferenceException(400, "A glyph can have at most five ranked meanings.");
        }

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                List<Map<String, Object>> existing = componentMeanings(conn, sessionContext.userId(), componentGlyph);
                if (existing.size() != orderedIds.size()) {
                    throw new PreferenceException(400, "Rank every saved meaning for this glyph.");
                }
                for (Map<String, Object> row : existing) {
                    if (!orderedIds.contains(row.get("id").toString())) {
                        throw new PreferenceException(400, "Ranked IDs must belong to this glyph.");
                    }
                }
                updateRanks(conn, sessionContext.userId(), componentGlyph, orderedIds);
                conn.commit();
                return componentMeaningsPayload(sessionContext, componentGlyph);
            } catch (PreferenceException | SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public Map<String, Object> replaceComponentMeanings(
            AuthService.SessionContext sessionContext, String glyph, Map<String, Object> request)
            throws SQLException, PreferenceException {
        requireConfigured();
        String componentGlyph = requirePathGlyph(glyph);
        List<DefinitionInput> definitions = definitionInputs(request);
        boolean useStandardDefinitionInMnemonics = booleanValue(request.get("useStandardDefinitionInMnemonics"), false);

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                Set<String> existingIds = existingIds(conn, sessionContext.userId(), componentGlyph);
                deleteMeaningsForGlyph(conn, sessionContext.userId(), componentGlyph);
                insertDefinitionInputs(conn, sessionContext.userId(), componentGlyph, existingIds, definitions);
                setStandardDefinitionPreference(conn, sessionContext.userId(), componentGlyph, useStandardDefinitionInMnemonics);
                conn.commit();
                return componentMeaningsPayload(sessionContext, componentGlyph);
            } catch (PreferenceException | SQLException ex) {
                conn.rollback();
                if (ex instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                    throw new PreferenceException(409, "Each saved definition for a glyph must be unique.");
                }
                throw ex;
            }
        }
    }

    private List<Map<String, Object>> standardDefinitionPreferences(AuthService.SessionContext sessionContext, String glyph)
            throws SQLException, PreferenceException {
        requireConfigured();
        try (Connection conn = connect()) {
            return standardDefinitionPreferences(conn, sessionContext.userId(), glyph);
        }
    }

    private boolean useStandardDefinitionInMnemonics(AuthService.SessionContext sessionContext, String glyph)
            throws SQLException, PreferenceException {
        requireConfigured();
        try (Connection conn = connect()) {
            return useStandardDefinitionInMnemonics(conn, sessionContext.userId(), glyph);
        }
    }

    private List<Map<String, Object>> componentMeanings(AuthService.SessionContext sessionContext, String glyph)
            throws SQLException, PreferenceException {
        requireConfigured();
        try (Connection conn = connect()) {
            return componentMeanings(conn, sessionContext.userId(), glyph);
        }
    }

    private List<Map<String, Object>> componentMeanings(Connection conn, UUID userId, String glyph) throws SQLException {
        String normalizedGlyph = blankToNull(glyph);
        String sql = normalizedGlyph == null
                ? """
                        SELECT id, component_glyph, component_token, meaning, rank, is_primary, use_in_mnemonics, notes, created_at, updated_at
                        FROM user_component_meanings
                        WHERE user_id = ?
                        ORDER BY component_glyph, rank, meaning
                        """
                : """
                        SELECT id, component_glyph, component_token, meaning, rank, is_primary, use_in_mnemonics, notes, created_at, updated_at
                        FROM user_component_meanings
                        WHERE user_id = ?
                          AND component_glyph = ?
                        ORDER BY rank, meaning
                        """;
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setObject(1, userId);
            if (normalizedGlyph != null) {
                statement.setString(2, normalizedGlyph);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> meanings = new ArrayList<>();
                while (rows.next()) {
                    meanings.add(meaningMap(rows));
                }
                return meanings;
            }
        }
    }

    private List<Map<String, Object>> standardDefinitionPreferences(Connection conn, UUID userId, String glyph) throws SQLException {
        String normalizedGlyph = blankToNull(glyph);
        String sql = normalizedGlyph == null
                ? """
                        SELECT component_glyph, use_in_mnemonics, updated_at
                        FROM user_standard_definition_preferences
                        WHERE user_id = ?
                          AND use_in_mnemonics
                        ORDER BY component_glyph
                        """
                : """
                        SELECT component_glyph, use_in_mnemonics, updated_at
                        FROM user_standard_definition_preferences
                        WHERE user_id = ?
                          AND component_glyph = ?
                          AND use_in_mnemonics
                        ORDER BY component_glyph
                        """;
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setObject(1, userId);
            if (normalizedGlyph != null) {
                statement.setString(2, normalizedGlyph);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> preferences = new ArrayList<>();
                while (rows.next()) {
                    preferences.add(standardDefinitionPreferenceMap(rows));
                }
                return preferences;
            }
        }
    }

    private boolean useStandardDefinitionInMnemonics(Connection conn, UUID userId, String glyph) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT use_in_mnemonics
                FROM user_standard_definition_preferences
                WHERE user_id = ?
                  AND component_glyph = ?
                LIMIT 1
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean("use_in_mnemonics");
            }
        }
    }

    private Map<String, Object> findMeaning(Connection conn, UUID userId, String glyph, String meaning) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT id, component_glyph, component_token, meaning, rank, is_primary, use_in_mnemonics, notes, created_at, updated_at
                FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                  AND meaning = ?
                LIMIT 1
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            statement.setString(3, meaning);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? meaningMap(rows) : null;
            }
        }
    }

    private Map<String, Object> findMeaningById(Connection conn, UUID userId, String glyph, String id) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT id, component_glyph, component_token, meaning, rank, is_primary, use_in_mnemonics, notes, created_at, updated_at
                FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                  AND id = ?::uuid
                LIMIT 1
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            statement.setString(3, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? meaningMap(rows) : null;
            }
        }
    }

    private Map<String, Object> upsertMeaning(
            Connection conn, UUID userId, String glyph, String token, String meaning, int rank, boolean isPrimary, boolean useInMnemonics, String notes)
            throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                INSERT INTO user_component_meanings (
                    user_id, component_glyph, component_token, meaning, rank, is_primary, use_in_mnemonics, notes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, component_glyph, meaning)
                DO UPDATE SET
                    component_token = EXCLUDED.component_token,
                    rank = user_component_meanings.rank,
                    is_primary = EXCLUDED.is_primary OR user_component_meanings.is_primary,
                    use_in_mnemonics = EXCLUDED.use_in_mnemonics OR user_component_meanings.use_in_mnemonics,
                    notes = COALESCE(EXCLUDED.notes, user_component_meanings.notes),
                    updated_at = now()
                RETURNING id, component_glyph, component_token, meaning, rank, is_primary, use_in_mnemonics, notes, created_at, updated_at
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            statement.setString(3, token);
            statement.setString(4, meaning);
            statement.setInt(5, rank);
            statement.setBoolean(6, isPrimary);
            statement.setBoolean(7, useInMnemonics);
            statement.setString(8, notes);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return meaningMap(rows);
            }
        }
    }

    private void requireMnemonicUsageCapacity(Connection conn, UUID userId, String glyph, String excludeId)
            throws SQLException, PreferenceException {
        String sql = excludeId == null
                ? """
                        SELECT COUNT(*) AS count
                        FROM user_component_meanings
                        WHERE user_id = ?
                          AND component_glyph = ?
                          AND use_in_mnemonics
                        """
                : """
                        SELECT COUNT(*) AS count
                        FROM user_component_meanings
                        WHERE user_id = ?
                          AND component_glyph = ?
                          AND use_in_mnemonics
                          AND id <> ?::uuid
                        """;
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            if (excludeId != null) {
                statement.setString(3, excludeId);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                if (rows.getInt("count") >= 3) {
                    throw new PreferenceException(409, "Choose up to three saved definitions for mnemonic generation.");
                }
            }
        }
    }

    private void updateMeaning(
            Connection conn, UUID userId, String glyph, String id, String meaning, String notes, boolean useInMnemonics)
            throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                UPDATE user_component_meanings
                SET meaning = ?,
                    notes = ?,
                    use_in_mnemonics = ?,
                    updated_at = now()
                WHERE user_id = ?
                  AND component_glyph = ?
                  AND id = ?::uuid
                """)) {
            statement.setString(1, meaning);
            statement.setString(2, notes);
            statement.setBoolean(3, useInMnemonics);
            statement.setObject(4, userId);
            statement.setString(5, glyph);
            statement.setString(6, id);
            statement.executeUpdate();
        }
    }

    private int deleteMeaning(Connection conn, UUID userId, String glyph, String id) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                DELETE FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                  AND id = ?::uuid
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            statement.setString(3, id);
            return statement.executeUpdate();
        }
    }

    private void deleteMeaningsForGlyph(Connection conn, UUID userId, String glyph) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                DELETE FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            statement.executeUpdate();
        }
    }

    private Set<String> existingIds(Connection conn, UUID userId, String glyph) throws SQLException {
        Set<String> ids = new HashSet<>();
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT id
                FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ids.add(rows.getString("id"));
                }
            }
        }
        return ids;
    }

    private void insertDefinitionInputs(
            Connection conn, UUID userId, String glyph, Set<String> existingIds, List<DefinitionInput> definitions)
            throws SQLException {
        if (definitions.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = conn.prepareStatement("""
                INSERT INTO user_component_meanings (
                    id, user_id, component_glyph, component_token, meaning, rank, is_primary, use_in_mnemonics, notes
                )
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int index = 0; index < definitions.size(); index++) {
                DefinitionInput definition = definitions.get(index);
                statement.setString(1, existingIds.contains(definition.id()) ? definition.id() : UUID.randomUUID().toString());
                statement.setObject(2, userId);
                statement.setString(3, glyph);
                statement.setString(4, definition.componentToken());
                statement.setString(5, definition.meaning());
                statement.setInt(6, index);
                statement.setBoolean(7, index == 0);
                statement.setBoolean(8, definition.useInMnemonics());
                statement.setString(9, definition.notes());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void setStandardDefinitionPreference(Connection conn, UUID userId, String glyph, boolean useInMnemonics)
            throws SQLException {
        if (!useInMnemonics) {
            try (PreparedStatement statement = conn.prepareStatement("""
                    DELETE FROM user_standard_definition_preferences
                    WHERE user_id = ?
                      AND component_glyph = ?
                    """)) {
                statement.setObject(1, userId);
                statement.setString(2, glyph);
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = conn.prepareStatement("""
                INSERT INTO user_standard_definition_preferences (
                    user_id, component_glyph, use_in_mnemonics
                )
                VALUES (?, ?, true)
                ON CONFLICT (user_id, component_glyph)
                DO UPDATE SET
                    use_in_mnemonics = true,
                    updated_at = now()
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            statement.executeUpdate();
        }
    }

    private int countMeanings(Connection conn, UUID userId, String glyph) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT COUNT(*) AS count
                FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt("count");
            }
        }
    }

    private int nextRank(Connection conn, UUID userId, String glyph, int requestedRank) throws SQLException {
        if (requestedRank >= 0) {
            return requestedRank;
        }
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT COALESCE(MAX(rank), -1) + 1 AS next_rank
                FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return Math.min(rows.getInt("next_rank"), MAX_MEANINGS_PER_COMPONENT - 1);
            }
        }
    }

    private void ensurePrimary(Connection conn, UUID userId, String glyph) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                UPDATE user_component_meanings
                SET is_primary = true,
                    updated_at = now()
                WHERE id = (
                    SELECT id
                    FROM user_component_meanings
                    WHERE user_id = ?
                      AND component_glyph = ?
                    ORDER BY rank, meaning
                    LIMIT 1
                )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM user_component_meanings
                    WHERE user_id = ?
                      AND component_glyph = ?
                      AND is_primary
                )
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            statement.setObject(3, userId);
            statement.setString(4, glyph);
            statement.executeUpdate();
        }
    }

    private void clearPrimary(Connection conn, UUID userId, String glyph) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                UPDATE user_component_meanings
                SET is_primary = false,
                    updated_at = now()
                WHERE user_id = ?
                  AND component_glyph = ?
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            statement.executeUpdate();
        }
    }

    private void updateRanks(Connection conn, UUID userId, String glyph, List<String> orderedIds) throws SQLException {
        StringBuilder placeholders = new StringBuilder();
        for (int index = 0; index < orderedIds.size(); index++) {
            if (index > 0) {
                placeholders.append(", ");
            }
            placeholders.append("?::uuid");
        }

        Map<String, StoredMeaning> stored = new LinkedHashMap<>();
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT id, component_token, meaning, use_in_mnemonics, notes, created_at
                FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                  AND id IN (%s)
                """.formatted(placeholders))) {
            int param = 1;
            statement.setObject(param++, userId);
            statement.setString(param++, glyph);
            for (String id : orderedIds) {
                statement.setString(param++, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    stored.put(rows.getString("id"), new StoredMeaning(
                            rows.getString("component_token"),
                            rows.getString("meaning"),
                            rows.getBoolean("use_in_mnemonics"),
                            rows.getString("notes"),
                            rows.getObject("created_at")));
                }
            }
        }
        try (PreparedStatement statement = conn.prepareStatement("""
                DELETE FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                  AND id IN (%s)
                """.formatted(placeholders))) {
            int param = 1;
            statement.setObject(param++, userId);
            statement.setString(param++, glyph);
            for (String id : orderedIds) {
                statement.setString(param++, id);
            }
            statement.executeUpdate();
        }
        try (PreparedStatement statement = conn.prepareStatement("""
                INSERT INTO user_component_meanings (
                    id, user_id, component_glyph, component_token, meaning, rank, is_primary, use_in_mnemonics, notes, created_at, updated_at
                )
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """)) {
            for (int index = 0; index < orderedIds.size(); index++) {
                String id = orderedIds.get(index);
                StoredMeaning meaning = stored.get(id);
                statement.setString(1, id);
                statement.setObject(2, userId);
                statement.setString(3, glyph);
                statement.setString(4, meaning.componentToken());
                statement.setString(5, meaning.meaning());
                statement.setInt(6, index);
                statement.setBoolean(7, index == 0);
                statement.setBoolean(8, meaning.useInMnemonics());
                statement.setString(9, meaning.notes());
                statement.setObject(10, meaning.createdAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void normalizeRanks(Connection conn, UUID userId, String glyph) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT id
                FROM user_component_meanings
                WHERE user_id = ?
                  AND component_glyph = ?
                ORDER BY rank, meaning
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, glyph);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ids.add(rows.getString("id"));
                }
            }
        }
        if (!ids.isEmpty()) {
            updateRanks(conn, userId, glyph, ids);
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

    private void requireConfigured() throws PreferenceException {
        if (dbUrl == null) {
            throw new PreferenceException(503, "Authentication database is not configured.");
        }
    }

    private static void loadPostgresDriver() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("PostgreSQL JDBC driver is not available.", ex);
        }
    }

    private static Map<String, Object> meaningMap(ResultSet rows) throws SQLException {
        Map<String, Object> meaning = new LinkedHashMap<>();
        meaning.put("id", rows.getString("id"));
        meaning.put("componentGlyph", rows.getString("component_glyph"));
        meaning.put("componentToken", rows.getString("component_token"));
        meaning.put("meaning", rows.getString("meaning"));
        meaning.put("rank", rows.getInt("rank"));
        meaning.put("isPrimary", rows.getBoolean("is_primary"));
        meaning.put("useInMnemonics", rows.getBoolean("use_in_mnemonics"));
        meaning.put("notes", rows.getString("notes"));
        meaning.put("createdAt", objectString(rows, "created_at"));
        meaning.put("updatedAt", objectString(rows, "updated_at"));
        return meaning;
    }

    private static Map<String, Object> standardDefinitionPreferenceMap(ResultSet rows) throws SQLException {
        Map<String, Object> preference = new LinkedHashMap<>();
        preference.put("componentGlyph", rows.getString("component_glyph"));
        preference.put("useInMnemonics", rows.getBoolean("use_in_mnemonics"));
        preference.put("updatedAt", objectString(rows, "updated_at"));
        return preference;
    }

    private static String normalizeRequiredString(Map<String, Object> request, String key, String message)
            throws PreferenceException {
        String value = blankToNull(stringValue(request, key));
        if (value == null && "glyph".equals(key)) {
            value = blankToNull(stringValue(request, "componentGlyph"));
        }
        if (value == null) {
            throw new PreferenceException(400, message);
        }
        return value;
    }

    private static String requirePathGlyph(String glyph) throws PreferenceException {
        String componentGlyph = blankToNull(glyph);
        if (componentGlyph == null) {
            throw new PreferenceException(400, "Component glyph is required.");
        }
        return componentGlyph;
    }

    private static String requirePreferenceId(String id) throws PreferenceException {
        String preferenceId = blankToNull(id);
        if (preferenceId == null) {
            throw new PreferenceException(400, "Preferred meaning ID is required.");
        }
        return preferenceId;
    }

    private static List<String> orderedIds(Map<String, Object> request) throws PreferenceException {
        Object idsValue = request.get("ids");
        if (idsValue == null) {
            idsValue = request.get("preferenceIds");
        }
        if (!(idsValue instanceof List<?> values)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object value : values) {
            String id = blankToNull(Objects.toString(value, null));
            if (id == null) {
                throw new PreferenceException(400, "Ranked preference IDs cannot be blank.");
            }
            ids.add(id);
        }
        return ids;
    }

    private static List<DefinitionInput> definitionInputs(Map<String, Object> request) throws PreferenceException {
        Object definitionsValue = request.get("definitions");
        if (!(definitionsValue instanceof List<?> values)) {
            throw new PreferenceException(400, "Definitions are required.");
        }
        if (values.size() > MAX_MEANINGS_PER_COMPONENT) {
            throw new PreferenceException(400, "A glyph can have at most five saved definitions.");
        }

        List<DefinitionInput> definitions = new ArrayList<>();
        Set<String> meanings = new HashSet<>();
        int mnemonicCount = 0;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> rawDefinition)) {
                throw new PreferenceException(400, "Each definition must be an object.");
            }
            String id = blankToNull(Objects.toString(rawDefinition.get("id"), null));
            String meaning = blankToNull(Objects.toString(rawDefinition.get("meaning"), null));
            if (meaning == null) {
                throw new PreferenceException(400, "Preferred meaning is required.");
            }
            requireMeaningLength(meaning);
            if (!meanings.add(meaning)) {
                throw new PreferenceException(409, "Each saved definition for a glyph must be unique.");
            }
            boolean useInMnemonics = booleanValue(rawDefinition.get("useInMnemonics"), false);
            if (useInMnemonics) {
                mnemonicCount++;
            }
            String componentToken = blankToNull(Objects.toString(rawDefinition.get("componentToken"), null));
            String notes = blankToNull(Objects.toString(rawDefinition.get("notes"), null));
            definitions.add(new DefinitionInput(id, meaning, componentToken, useInMnemonics, notes));
        }
        if (mnemonicCount > 3) {
            throw new PreferenceException(409, "Choose up to three saved definitions for mnemonic generation.");
        }
        return definitions;
    }

    private static int optionalRank(Object value) throws PreferenceException {
        if (value == null || value.toString().isBlank()) {
            return -1;
        }
        int rank;
        try {
            rank = Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            throw new PreferenceException(400, "Rank must be a number from 0 to 4.");
        }
        if (rank < 0 || rank >= MAX_MEANINGS_PER_COMPONENT) {
            throw new PreferenceException(400, "Rank must be a number from 0 to 4.");
        }
        return rank;
    }

    private static void requireMeaningLength(String meaning) throws PreferenceException {
        if (meaning.length() > MAX_MEANING_LENGTH) {
            throw new PreferenceException(400, "Preferred meaning must be 50 characters or fewer.");
        }
    }

    private static int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    private static String objectString(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        return value == null ? null : value.toString();
    }

    private static String stringValue(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? null : value.toString();
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record StoredMeaning(String componentToken, String meaning, boolean useInMnemonics, String notes, Object createdAt) {}

    private record DefinitionInput(String id, String meaning, String componentToken, boolean useInMnemonics, String notes) {}

    public static final class PreferenceException extends Exception {
        private final int status;

        public PreferenceException(int status, String message) {
            super(Objects.requireNonNull(message));
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
