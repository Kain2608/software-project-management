package com.smartelectronicsupermarket.security.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcPermissionCheckerTest {

    private static final String HAS_PERMISSION_SQL = """
            SELECT sasms.has_permission(
                CAST(? AS uuid),
                CAST(? AS text),
                CAST(? AS uuid)
            )
            """;

    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private JdbcTemplate jdbcTemplate;
    private JdbcPermissionChecker permissionChecker;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        permissionChecker = new JdbcPermissionChecker(jdbcTemplate);
    }

    @Test
    void grantsOnlyWhenDatabaseReturnsTrueForGlobalScope() {
        when(jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                "catalog.write",
                (UUID) null)).thenReturn(true);

        boolean granted = permissionChecker.hasPermission(
                ACTOR_ID, "catalog.write", null);

        assertTrue(granted);
        verify(jdbcTemplate).queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                "catalog.write",
                (UUID) null);
    }

    @Test
    void deniesWhenDatabaseReportsMissingPermission() {
        when(jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                "catalog.write",
                STORE_ID)).thenReturn(false);

        assertFalse(permissionChecker.hasPermission(
                ACTOR_ID, "catalog.write", STORE_ID));
    }

    @Test
    void deniesInactiveActorWhenDatabaseReturnsFalse() {
        when(jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                "inventory.read",
                STORE_ID)).thenReturn(false);

        assertFalse(permissionChecker.hasPermission(
                ACTOR_ID, "inventory.read", STORE_ID));
    }

    @Test
    void bindsStoreIdUnchanged() {
        when(jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                "inventory.receive",
                STORE_ID)).thenReturn(true);

        assertTrue(permissionChecker.hasPermission(
                ACTOR_ID, "inventory.receive", STORE_ID));
        verify(jdbcTemplate).queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                "inventory.receive",
                STORE_ID);
    }

    @Test
    void deniesWhenDatabaseReturnsNull() {
        when(jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                "catalog.read",
                STORE_ID)).thenReturn(null);

        assertFalse(permissionChecker.hasPermission(
                ACTOR_ID, "catalog.read", STORE_ID));
    }

    @Test
    void propagatesDatabaseFailureWithoutRetryOrFallback() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                "catalog.read",
                STORE_ID)).thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> permissionChecker.hasPermission(
                        ACTOR_ID, "catalog.read", STORE_ID));

        assertSame(failure, thrown);
        verify(jdbcTemplate, times(1)).queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                "catalog.read",
                STORE_ID);
    }

    @Test
    void bindsPermissionCodeAsDataInsteadOfSqlText() {
        String untrustedPermission = "catalog.read') OR true --";
        when(jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                untrustedPermission,
                STORE_ID)).thenReturn(false);

        assertFalse(permissionChecker.hasPermission(
                ACTOR_ID, untrustedPermission, STORE_ID));
        verify(jdbcTemplate).queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                ACTOR_ID,
                untrustedPermission,
                STORE_ID);
    }

    @Test
    void rejectsMissingInternalInputsWithoutCallingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> permissionChecker.hasPermission(null, "catalog.read", STORE_ID));
        assertThrows(IllegalArgumentException.class,
                () -> permissionChecker.hasPermission(ACTOR_ID, null, STORE_ID));
        assertThrows(IllegalArgumentException.class,
                () -> permissionChecker.hasPermission(ACTOR_ID, " ", STORE_ID));

        verifyNoInteractions(jdbcTemplate);
    }
}
