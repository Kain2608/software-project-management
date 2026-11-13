package com.smartelectronicsupermarket.pos.selection.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;

class JdbcPosDeviceSelectionDataAccessTest {

    private static final String LOCK_DEVICE_SQL = """
            SELECT sasms.find_and_lock_device_for_pos(
                CAST(? AS uuid),
                CAST(? AS uuid),
                CAST(? AS uuid),
                CAST(? AS text),
                CAST(? AS uuid)
            )
            """;
    private static final String RELEASE_DEVICE_SQL = """
            SELECT sasms.release_device_lock(
                CAST(? AS uuid),
                CAST(? AS uuid),
                CAST(? AS uuid)
            )
            FROM sasms.sales_orders o
            WHERE o.id = CAST(? AS uuid)
              AND sasms.has_permission(
                    CAST(? AS uuid),
                    'sales.checkout',
                    o.store_id
                  ) = TRUE
            """;
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID ORDER_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000904");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000905");

    private JdbcTemplate jdbcTemplate;
    private JdbcPosDeviceSelectionDataAccess dataAccess;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        dataAccess = new JdbcPosDeviceSelectionDataAccess(jdbcTemplate);
    }

    @Test
    void callsLockFunctionWithExactContractAndPreservesSerial() {
        String serial = "  Serial-Abc  ";
        when(jdbcTemplate.queryForObject(
                LOCK_DEVICE_SQL,
                UUID.class,
                STORE_ID,
                ORDER_ID,
                ORDER_ITEM_ID,
                serial,
                ACTOR_ID)).thenReturn(DEVICE_ID);

        assertEquals(
                DEVICE_ID,
                dataAccess.lockDevice(STORE_ID, ORDER_ID, ORDER_ITEM_ID, serial, ACTOR_ID));
        verify(jdbcTemplate, times(1)).queryForObject(
                LOCK_DEVICE_SQL,
                UUID.class,
                STORE_ID,
                ORDER_ID,
                ORDER_ITEM_ID,
                serial,
                ACTOR_ID);
    }

    @Test
    void releaseChecksCheckoutPermissionAtOrderStoreAndBindsExactValues() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(1, Boolean.class)).thenReturn(Boolean.TRUE);
        stubReleaseCallbacks(statement, resultSet);

        assertTrue(dataAccess.releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID));
        verify(statement).setObject(1, ORDER_ID);
        verify(statement).setObject(2, DEVICE_ID);
        verify(statement).setObject(3, ACTOR_ID);
        verify(statement).setObject(4, ORDER_ID);
        verify(statement).setObject(5, ACTOR_ID);
    }

    @Test
    void releaseFailsClosedWhenOrderIsMissingOrActorIsOutOfScope() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        stubReleaseCallbacks(statement, resultSet);

        assertFalse(dataAccess.releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID));
    }

    @Test
    void propagatesDatabaseFailureWithoutRetryOrFallback() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(jdbcTemplate.queryForObject(
                LOCK_DEVICE_SQL,
                UUID.class,
                STORE_ID,
                ORDER_ID,
                ORDER_ITEM_ID,
                "SERIAL-1",
                ACTOR_ID)).thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> dataAccess.lockDevice(
                        STORE_ID, ORDER_ID, ORDER_ITEM_ID, "SERIAL-1", ACTOR_ID));

        assertSame(failure, thrown);
    }

    private void stubReleaseCallbacks(PreparedStatement statement, ResultSet resultSet) {
        when(jdbcTemplate.query(
                eq(RELEASE_DEVICE_SQL),
                ArgumentMatchers.<PreparedStatementSetter>any(),
                ArgumentMatchers.<ResultSetExtractor<Boolean>>any())).thenAnswer(invocation -> {
                    PreparedStatementSetter setter = invocation.getArgument(1);
                    ResultSetExtractor<Boolean> extractor = invocation.getArgument(2);
                    setter.setValues(statement);
                    return extractor.extractData(resultSet);
                });
    }
}
