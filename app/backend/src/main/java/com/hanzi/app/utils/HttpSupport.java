package com.hanzi.app.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class HttpSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpSupport() {}

    public static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(payload);
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    public static void sendNoContent(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
    }

    public static Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        if (exchange.getRequestHeaders().getFirst("Content-Length") != null
                && "0".equals(exchange.getRequestHeaders().getFirst("Content-Length"))) {
            return Map.of();
        }
        return MAPPER.readValue(exchange.getRequestBody(), new TypeReference<>() {});
    }

    public static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    public static Map<String, String> queryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String part : query.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            String[] pieces = part.split("=", 2);
            String key = decodeQuery(pieces[0]);
            String value = pieces.length > 1 ? decodeQuery(pieces[1]) : "";
            if (!value.isEmpty()) {
                params.put(key, value);
            }
        }
        return params;
    }

    public static String decodeQuery(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static String decodePath(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    public static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String bearerToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    public static String userAgent(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst("User-Agent");
    }

    public static String clientIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        InetSocketAddress remote = exchange.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
    }
}
