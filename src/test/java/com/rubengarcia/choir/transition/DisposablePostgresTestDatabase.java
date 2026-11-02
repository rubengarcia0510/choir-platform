package com.rubengarcia.choir.transition;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;

final class DisposablePostgresTestDatabase {

    private static final String DATABASE_PREFIX = "choir_test_";

    private final String databaseName =
            DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");
    private String adminJdbcUrl;
    private String username;
    private String password;
    private boolean created;

    void configure(DynamicPropertyRegistry registry) {
        if (!isProductionProfileActive()) {
            return;
        }

        adminJdbcUrl = requiredEnvironmentVariable("DATABASE_URL");
        String r2dbcUrl = requiredEnvironmentVariable("R2DBC_URL");
        username = requiredEnvironmentVariable("DATABASE_USERNAME");
        password = requiredEnvironmentVariable("DATABASE_PASSWORD");

        try (Connection connection = openAdminConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + databaseName + "\"");
            created = true;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "No se pudo crear la base PostgreSQL descartable "
                            + databaseName,
                    exception);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(
                this::dropOnShutdown,
                "drop-" + databaseName));

        registry.add("spring.datasource.url",
                () -> replaceDatabase(adminJdbcUrl, databaseName));
        registry.add("spring.r2dbc.url",
                () -> replaceDatabase(r2dbcUrl, databaseName));
    }

    synchronized void drop() throws SQLException {
        if (!created) {
            return;
        }

        try (Connection connection = openAdminConnection();
                PreparedStatement terminateConnections = connection.prepareStatement("""
                        SELECT pg_terminate_backend(pid)
                        FROM pg_stat_activity
                        WHERE datname = ? AND pid <> pg_backend_pid()
                        """)) {
            terminateConnections.setString(1, databaseName);
            terminateConnections.execute();
        }

        try (Connection connection = openAdminConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS \"" + databaseName + "\"");
        }

        try (Connection connection = openAdminConnection();
                PreparedStatement verifyDropped = connection.prepareStatement("""
                        SELECT COUNT(*) FROM pg_database WHERE datname = ?
                        """)) {
            verifyDropped.setString(1, databaseName);
            try (var result = verifyDropped.executeQuery()) {
                if (!result.next() || result.getInt(1) != 0) {
                    throw new SQLException(
                            "La base PostgreSQL descartable no fue eliminada: "
                                    + databaseName);
                }
            }
        }
        created = false;
    }

    private Connection openAdminConnection() throws SQLException {
        return DriverManager.getConnection(adminJdbcUrl, username, password);
    }

    private void dropOnShutdown() {
        try {
            drop();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "No se pudo eliminar la base PostgreSQL descartable "
                            + databaseName,
                    exception);
        }
    }

    private static boolean isProductionProfileActive() {
        String activeProfiles = System.getProperty(
                "spring.profiles.active",
                System.getenv("SPRING_PROFILES_ACTIVE"));
        return activeProfiles != null
                && List.of(activeProfiles.split(",")).stream()
                        .map(String::trim)
                        .anyMatch("prod"::equalsIgnoreCase);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "La variable de entorno " + name
                            + " es obligatoria para las pruebas PostgreSQL");
        }
        return value;
    }

    private static String replaceDatabase(String url, String databaseName) {
        int queryStart = url.indexOf('?');
        int pathStart = url.lastIndexOf(
                '/', queryStart < 0 ? url.length() - 1 : queryStart - 1);
        if (pathStart < 0 || pathStart == url.length() - 1) {
            throw new IllegalArgumentException(
                    "La URL PostgreSQL no contiene un nombre de base de datos");
        }

        int databaseEnd = queryStart < 0 ? url.length() : queryStart;
        return url.substring(0, pathStart + 1)
                + databaseName
                + url.substring(databaseEnd);
    }
}
