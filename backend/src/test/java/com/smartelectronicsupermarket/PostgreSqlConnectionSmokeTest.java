package com.smartelectronicsupermarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PostgreSqlConnectionSmokeTest {

    private static final String ENABLE_ENV = "SASMS_PG_SMOKE_ENABLED";
    private static final String URL_ENV = "SASMS_DB_URL";
    private static final String USERNAME_ENV = "SASMS_DB_USERNAME";
    private static final String PASSWORD_ENV = "SASMS_DB_PASSWORD";

    @Test
    void connectsToValidatedLocalSasmsDatabaseReadOnly() throws SQLException {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv(ENABLE_ENV)),
                () -> "PostgreSQL smoke test is disabled; set " + ENABLE_ENV + "=true to run it");

        String jdbcUrl = requiredEnvironment(URL_ENV);
        String username = requiredEnvironment(USERNAME_ENV);
        String password = requiredEnvironment(PASSWORD_ENV);

        assertSafeLocalSasmsTarget(jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);

            try {
                assertEquals("on", queryString(connection, "SHOW transaction_read_only"));

                String serverVersion = queryString(connection, "SHOW server_version");
                assertTrue(serverVersion.startsWith("16."),
                        () -> "Expected PostgreSQL 16.x but server reported " + serverVersion);

                assertEquals("sasms", queryString(connection, "SELECT current_database()"));
                assertEquals(37L, queryLong(connection,
                        "SELECT count(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'sasms' AND table_type = 'BASE TABLE'"));
                assertEquals(9L, queryLong(connection, "SELECT count(*) FROM sasms.roles"));
                assertEquals(2L, queryLong(connection, "SELECT count(*) FROM sasms.products"));
            } finally {
                connection.rollback();
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assumeTrue(value != null && !value.isBlank(), () -> name + " is required when the smoke test is enabled");
        return value;
    }

    private static void assertSafeLocalSasmsTarget(String jdbcUrl) {
        assertTrue(jdbcUrl.startsWith("jdbc:postgresql://"), "Only a PostgreSQL JDBC URL is allowed");

        URI target = URI.create(jdbcUrl.substring("jdbc:".length()));
        assertEquals("postgresql", target.getScheme());
        assertTrue(Set.of("127.0.0.1", "localhost", "::1").contains(target.getHost()),
                "Smoke test is restricted to a loopback PostgreSQL host");
        assertEquals(5432, target.getPort() == -1 ? 5432 : target.getPort(),
                "Smoke test is restricted to PostgreSQL port 5432");
        assertEquals("/sasms", target.getPath(), "Smoke test is restricted to database sasms");
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), "Expected one result row");
            return result.getString(1);
        }
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), "Expected one result row");
            return result.getLong(1);
        }
    }
}
