package com.hanzi.app;

import com.sun.net.httpserver.HttpExchange;
import com.hanzi.app.services.AuthService;
import com.hanzi.app.services.AuthService.AuthException;
import com.hanzi.app.services.DictionaryService;
import com.hanzi.app.services.PreferenceService;
import com.hanzi.app.utils.HttpSupport;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

final class ApiRoutes {
    private final DictionaryService dictionary;
    private final PreferenceService preferences;
    private final AuthService auth;

    ApiRoutes(
            DictionaryService dictionary,
            PreferenceService preferences,
            AuthService auth) {
        this.dictionary = dictionary;
        this.preferences = preferences;
        this.auth = auth;
    }

    void handle(HttpExchange exchange, String path, String rawPath, Map<String, String> params)
            throws IOException, SQLException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equals(method)) {
            HttpSupport.sendNoContent(exchange);
            return;
        }

        if (isGet(method, path, "/api/health")) {
            HttpSupport.sendJson(exchange, 200, healthPayload());
            return;
        }

        if (isGet(method, path, "/api/metadata") || isGet(method, path, "/api/explore/metadata")) {
            HttpSupport.sendJson(exchange, 200, dictionary.metadata());
            return;
        }

        if (isGet(method, path, "/api/search") || isGet(method, path, "/api/explore/search")) {
            int limit = HttpSupport.clamp(HttpSupport.parseInt(params.getOrDefault("limit", "40"), 40), 1, 120);
            HttpSupport.sendJson(exchange, 200, Map.of("results", dictionary.search(
                    params.getOrDefault("q", ""),
                    params.get("hsk"),
                    params.get("stroke_min"),
                    params.get("stroke_max"),
                    limit)));
            return;
        }

        if ("GET".equals(method) && path.startsWith("/api/glyph/")) {
            sendCharacterDetail(exchange, HttpSupport.decodePath(rawPath.substring("/api/glyph/".length())));
            return;
        }

        if ("GET".equals(method) && path.startsWith("/api/characters/")) {
            sendCharacterDetail(exchange, HttpSupport.decodePath(rawPath.substring("/api/characters/".length())));
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
            sendStub(exchange, preferences.notImplemented(operation(method, path)));
            return;
        }

        if (path.startsWith("/api/auth")) {
            handleAuth(exchange, method, path);
            return;
        }

        HttpSupport.sendJson(exchange, 404, Map.of("error", "Unknown API route"));
    }

    private void sendCharacterDetail(HttpExchange exchange, String key) throws IOException, SQLException {
        Map<String, Object> entry = dictionary.characterDetail(key);
        if (entry == null) {
            HttpSupport.sendJson(exchange, 404, Map.of("error", "Glyph not found"));
            return;
        }
        HttpSupport.sendJson(exchange, 200, entry);
    }

    private void sendStub(HttpExchange exchange, Map<String, Object> payload) throws IOException {
        HttpSupport.sendJson(exchange, 501, payload);
    }

    private void handleAuth(HttpExchange exchange, String method, String path) throws IOException, SQLException {
        try {
            if ("POST".equals(method) && "/api/auth/register".equals(path)) {
                HttpSupport.sendJson(exchange, 201, auth.register(
                        HttpSupport.readJsonObject(exchange),
                        HttpSupport.userAgent(exchange),
                        HttpSupport.clientIp(exchange)));
                return;
            }

            if ("POST".equals(method) && "/api/auth/sign-in".equals(path)) {
                HttpSupport.sendJson(exchange, 200, auth.signIn(
                        HttpSupport.readJsonObject(exchange),
                        HttpSupport.userAgent(exchange),
                        HttpSupport.clientIp(exchange)));
                return;
            }

            if ("POST".equals(method) && "/api/auth/sign-out".equals(path)) {
                HttpSupport.sendJson(exchange, 200, auth.signOut(HttpSupport.bearerToken(exchange)));
                return;
            }

            if ("GET".equals(method) && "/api/auth/session".equals(path)) {
                HttpSupport.sendJson(exchange, 200, auth.session(HttpSupport.bearerToken(exchange)));
                return;
            }

            HttpSupport.sendJson(exchange, 404, Map.of("error", "Unknown auth route"));
        } catch (AuthException ex) {
            HttpSupport.sendJson(exchange, ex.status(), Map.of("error", ex.getMessage()));
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
