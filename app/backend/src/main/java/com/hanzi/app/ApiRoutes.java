package com.hanzi.app;

import com.sun.net.httpserver.HttpExchange;
import com.hanzi.app.services.AnkiSyncService;
import com.hanzi.app.services.AuthService;
import com.hanzi.app.services.AuthService.AuthException;
import com.hanzi.app.services.DictionaryService;
import com.hanzi.app.services.FlashcardService;
import com.hanzi.app.services.MnemonicService;
import com.hanzi.app.services.PreferenceService;
import com.hanzi.app.utils.HttpHelper;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiRoutes {
    private final DictionaryService dictionary;
    private final FlashcardService flashcards;
    private final MnemonicService mnemonics;
    private final AnkiSyncService ankiSync;
    private final PreferenceService preferences;
    private final AuthService auth;

    public ApiRoutes(
            DictionaryService dictionary,
            FlashcardService flashcards,
            MnemonicService mnemonics,
            AnkiSyncService ankiSync,
            PreferenceService preferences,
            AuthService auth) {
        this.dictionary = dictionary;
        this.flashcards = flashcards;
        this.mnemonics = mnemonics;
        this.ankiSync = ankiSync;
        this.preferences = preferences;
        this.auth = auth;
    }

    public void handle(HttpExchange exchange, String path, String rawPath, Map<String, String> params)
            throws IOException, SQLException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equals(method)) {
            HttpHelper.sendNoContent(exchange);
            return;
        }

        if (isGet(method, path, "/api/health")) {
            HttpHelper.sendJson(exchange, 200, healthPayload());
            return;
        }

        if (isGet(method, path, "/api/metadata") || isGet(method, path, "/api/explore/metadata")) {
            HttpHelper.sendJson(exchange, 200, dictionary.metadata());
            return;
        }

        if (isGet(method, path, "/api/search") || isGet(method, path, "/api/explore/search")) {
            int limit = HttpHelper.clamp(HttpHelper.parseInt(params.getOrDefault("limit", "40"), 40), 1, 120);
            HttpHelper.sendJson(exchange, 200, Map.of("results", dictionary.search(
                    params.getOrDefault("q", ""),
                    params.get("hsk"),
                    params.get("stroke_min"),
                    params.get("stroke_max"),
                    limit)));
            return;
        }

        if ("GET".equals(method) && path.startsWith("/api/glyph/")) {
            sendCharacterDetail(exchange, HttpHelper.decodePath(rawPath.substring("/api/glyph/".length())));
            return;
        }

        if ("GET".equals(method) && path.startsWith("/api/characters/")) {
            sendCharacterDetail(exchange, HttpHelper.decodePath(rawPath.substring("/api/characters/".length())));
            return;
        }

        if (path.startsWith("/api/cards")) {
            sendStub(exchange, flashcards.notImplemented(operation(method, path)));
            return;
        }

        if (path.startsWith("/api/mnemonics")) {
            sendStub(exchange, mnemonics.notImplemented(operation(method, path)));
            return;
        }

        if (path.startsWith("/api/anki")) {
            sendStub(exchange, ankiSync.notImplemented(operation(method, path)));
            return;
        }

        if (path.startsWith("/api/preferences")) {
            handlePreferences(exchange, method, path);
            return;
        }

        if (path.startsWith("/api/auth")) {
            handleAuth(exchange, method, path);
            return;
        }

        HttpHelper.sendJson(exchange, 404, Map.of("error", "Unknown API route"));
    }

    private void sendCharacterDetail(HttpExchange exchange, String key) throws IOException, SQLException {
        Map<String, Object> entry = dictionary.characterDetail(key);
        if (entry == null) {
            HttpHelper.sendJson(exchange, 404, Map.of("error", "Glyph not found"));
            return;
        }
        HttpHelper.sendJson(exchange, 200, entry);
    }

    private void sendStub(HttpExchange exchange, Map<String, Object> payload) throws IOException {
        HttpHelper.sendJson(exchange, 501, payload);
    }

    private void handleAuth(HttpExchange exchange, String method, String path) throws IOException, SQLException {
        try {
            if ("POST".equals(method) && "/api/auth/register".equals(path)) {
                HttpHelper.sendJson(exchange, 201, auth.register(
                        HttpHelper.readJsonObject(exchange),
                        HttpHelper.userAgent(exchange),
                        HttpHelper.clientIp(exchange)));
                return;
            }

            if ("POST".equals(method) && "/api/auth/sign-in".equals(path)) {
                HttpHelper.sendJson(exchange, 200, auth.signIn(
                        HttpHelper.readJsonObject(exchange),
                        HttpHelper.userAgent(exchange),
                        HttpHelper.clientIp(exchange)));
                return;
            }

            if ("POST".equals(method) && "/api/auth/sign-out".equals(path)) {
                HttpHelper.sendJson(exchange, 200, auth.signOut(HttpHelper.bearerToken(exchange)));
                return;
            }

            if ("GET".equals(method) && "/api/auth/session".equals(path)) {
                HttpHelper.sendJson(exchange, 200, auth.session(HttpHelper.bearerToken(exchange)));
                return;
            }

            HttpHelper.sendJson(exchange, 404, Map.of("error", "Unknown auth route"));
        } catch (AuthException ex) {
            HttpHelper.sendJson(exchange, ex.status(), Map.of("error", ex.getMessage()));
        }
    }

    private void handlePreferences(HttpExchange exchange, String method, String path) throws IOException, SQLException {
        try {
            AuthService.SessionContext session = auth.requireSession(HttpHelper.bearerToken(exchange));
            if (isGet(method, path, "/api/preferences")) {
                HttpHelper.sendJson(exchange, 200, preferences.overview(session));
                return;
            }

            sendStub(exchange, preferences.notImplemented(operation(method, path)));
        } catch (AuthException ex) {
            HttpHelper.sendJson(exchange, ex.status(), Map.of("error", ex.getMessage()));
        }
    }

    private Map<String, Object> healthPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("app", "hanzi-cardgen");
        payload.put("implemented_services", Map.of(
                "explore", true,
                "character_detail", true,
                "auth", auth.isConfigured()));
        payload.put("scaffolded_services", Map.of(
                "flashcard_creation", true,
                "mnemonic_generation", true,
                "anki_sync", true,
                "preferences", true,
                "auth", !auth.isConfigured()));
        return payload;
    }

    private static boolean isGet(String method, String path, String expectedPath) {
        return "GET".equals(method) && expectedPath.equals(path);
    }

    private static String operation(String method, String path) {
        return method + " " + path;
    }
}
