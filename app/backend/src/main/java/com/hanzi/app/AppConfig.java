package com.hanzi.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

record AppConfig(Path dbPath, Path staticDir, String host, int port, String appDbUrl, String appDbUser, String appDbPassword) {
    static final int DEFAULT_PORT = 8766;

    static AppConfig parse(String[] args) {
        Path root = defaultRoot();
        Path db = root.resolve("dictionary/dict.sqlite3").normalize();
        Path staticDir = root.resolve("app/frontend/dist").normalize();
        String host = "127.0.0.1";
        int port = DEFAULT_PORT;
        String appDbUrl = firstEnv("HANZI_APP_DB_URL", "DATABASE_URL");
        String appDbUser = firstEnv("HANZI_APP_DB_USER", "PGUSER");
        String appDbPassword = firstEnv("HANZI_APP_DB_PASSWORD", "PGPASSWORD");

        List<String> values = List.of(args);
        for (int i = 0; i < values.size(); i++) {
            switch (values.get(i)) {
                case "--db" -> db = Path.of(requireValue(values, ++i, "--db")).toAbsolutePath().normalize();
                case "--static-dir" -> staticDir = Path.of(requireValue(values, ++i, "--static-dir")).toAbsolutePath().normalize();
                case "--host" -> host = requireValue(values, ++i, "--host");
                case "--port" -> port = Integer.parseInt(requireValue(values, ++i, "--port"));
                case "--app-db-url" -> appDbUrl = requireValue(values, ++i, "--app-db-url");
                case "--app-db-user" -> appDbUser = requireValue(values, ++i, "--app-db-user");
                case "--app-db-password" -> appDbPassword = requireValue(values, ++i, "--app-db-password");
                default -> throw new IllegalArgumentException("Unknown argument: " + values.get(i));
            }
        }
        return new AppConfig(
                db, staticDir, host, port, normalizeBlank(appDbUrl), normalizeBlank(appDbUser), normalizeBlank(appDbPassword));
    }

    private static String requireValue(List<String> values, int index, String name) {
        if (index >= values.size()) {
            throw new IllegalArgumentException("Missing value for " + name);
        }
        return values.get(index);
    }

    private static Path defaultRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("dictionary/dict.sqlite3"))) {
            return cwd;
        }
        Path maybeApp = cwd.getParent();
        if (maybeApp != null) {
            Path maybeRoot = maybeApp.getParent();
            if (maybeRoot != null && Files.exists(maybeRoot.resolve("dictionary/dict.sqlite3"))) {
                return maybeRoot;
            }
        }
        return cwd;
    }

    private static String firstEnv(String first, String second) {
        String value = normalizeBlank(System.getenv(first));
        return value == null ? normalizeBlank(System.getenv(second)) : value;
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
