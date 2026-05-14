package com.hanzi.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.hanzi.app.services.AnkiSyncService;
import com.hanzi.app.services.AuthService;
import com.hanzi.app.services.DictionaryService;
import com.hanzi.app.services.FlashcardService;
import com.hanzi.app.services.MnemonicService;
import com.hanzi.app.services.PreferenceService;
import com.hanzi.app.utils.HttpHelper;
import com.hanzi.app.utils.StaticFileHandler;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Executors;

public final class HanziCardgenServer {
    private final ApiRoutes apiRoutes;
    private final StaticFileHandler staticFiles;

    private HanziCardgenServer(ApiRoutes apiRoutes, StaticFileHandler staticFiles) {
        this.apiRoutes = apiRoutes;
        this.staticFiles = staticFiles;
    }

    public static void main(String[] args) throws IOException {
        AppConfig config = AppConfig.parse(args);
        if (!Files.exists(config.dbPath())) {
            throw new IllegalArgumentException("Dictionary database not found: " + config.dbPath());
        }

        HanziCardgenServer app = build(config);
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        } catch (BindException ex) {
            throw new BindException("Port " + config.port() + " is already in use on " + config.host()
                    + ". If AnkiConnect is using 8765, start the app with --port " + AppConfig.DEFAULT_PORT
                    + " or choose another free port.");
        }
        server.createContext("/", app::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.printf("Hanzi Cardgen backend running at http://%s:%d%n", config.host(), config.port());
        System.out.printf("Using dictionary database %s%n", config.dbPath());
        if (Files.exists(config.staticDir())) {
            System.out.printf("Serving frontend from %s%n", config.staticDir());
        } else {
            System.out.printf("Frontend build directory not found: %s%n", config.staticDir());
        }
    }

    private static HanziCardgenServer build(AppConfig config) {
        DictionaryService dictionary = new DictionaryService(new GlyphStore(config.dbPath()));
        ApiRoutes apiRoutes = new ApiRoutes(
                dictionary,
                new FlashcardService(),
                new MnemonicService(),
                new AnkiSyncService(),
                new PreferenceService(config.appDbUrl(), config.appDbUser(), config.appDbPassword()),
                new AuthService(config.appDbUrl(), config.appDbUser(), config.appDbPassword()));
        return new HanziCardgenServer(apiRoutes, new StaticFileHandler(config.staticDir()));
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String rawPath = exchange.getRequestURI().getRawPath();
            String path = normalizePath(HttpHelper.decodePath(rawPath));
            String routeRawPath = normalizePath(rawPath);
            if (path.startsWith("/api/")) {
                apiRoutes.handle(exchange, path, routeRawPath, HttpHelper.queryParams(exchange.getRequestURI()));
            } else if ("GET".equals(exchange.getRequestMethod()) || "HEAD".equals(exchange.getRequestMethod())) {
                staticFiles.handle(exchange, path);
            } else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                HttpHelper.sendNoContent(exchange);
            } else {
                HttpHelper.sendJson(exchange, 405, Map.of(
                        "error", "Method not allowed",
                        "method", exchange.getRequestMethod(),
                        "path", path));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            HttpHelper.sendJson(exchange, 500, Map.of("error", message));
        } finally {
            exchange.close();
        }
    }

    private static String normalizePath(String path) {
        return path == null ? "/" : path.replaceAll("/{2,}", "/");
    }
}
