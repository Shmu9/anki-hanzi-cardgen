package com.hanzi.app.utils;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class StaticFileHandler {
    private final Path staticDir;

    public StaticFileHandler(Path staticDir) {
        this.staticDir = staticDir.toAbsolutePath().normalize();
    }

    public void handle(HttpExchange exchange, String path) throws IOException {
        Path target = staticDir.resolve(path == null || path.equals("/") ? "index.html" : path.substring(1)).normalize();
        if (!target.startsWith(staticDir) || !Files.exists(target) || Files.isDirectory(target)) {
            target = staticDir.resolve("index.html").normalize();
        }
        if (!target.startsWith(staticDir) || !Files.exists(target)) {
            HttpHelper.sendJson(exchange, 404, Map.of("error", "Frontend build not found"));
            return;
        }

        byte[] body = Files.readAllBytes(target);
        HttpHelper.addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType(target));
        HttpHelper.sendResponseHeaders(exchange, 200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String contentType(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}
