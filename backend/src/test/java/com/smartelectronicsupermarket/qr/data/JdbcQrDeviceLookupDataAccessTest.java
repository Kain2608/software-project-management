package com.smartelectronicsupermarket.qr.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;

class JdbcQrDeviceLookupDataAccessTest {

    private static final String FIND_BY_QR_TOKEN_SQL = """
            SELECT
                d.id,
                d.product_id,
                d.warehouse_id,
                d.status
            FROM sasms.devices d
            JOIN sasms.warehouses w
                ON w.id = d.warehouse_id
            WHERE d.qr_token = CAST(? AS uuid)
              AND sasms.has_permission(
                  CAST(? AS uuid),
                  'inventory.read',
                  w.store_id
              ) = TRUE
            """;

    private static final UUID QR_TOKEN =
            UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000032");
    private static final UUID DEVICE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final UUID PRODUCT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000034");
    private static final UUID WAREHOUSE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000035");

    private JdbcTemplate jdbcTemplate;
    private JdbcQrDeviceLookupDataAccess dataAccess;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        dataAccess = new JdbcQrDeviceLookupDataAccess(jdbcTemplate);
    }

    @Test
    void queryUsesQrTokenWarehouseStoreScopeAndOnlyMinimalColumns() {
        String normalized = FIND_BY_QR_TOKEN_SQL
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertTrue(normalized.contains("from sasms.devices d"));
        assertTrue(normalized.contains(
                "join sasms.warehouses w on w.id = d.warehouse_id"));
        assertTrue(normalized.contains("where d.qr_token = cast(? as uuid)"));
        assertTrue(normalized.contains(
                "sasms.has_permission( cast(? as uuid), 'inventory.read', w.store_id ) = true"));
        assertFalse(normalized.contains("where d.id ="));
        assertFalse(normalized.contains("d.serial"));
        assertFalse(normalized.contains("d.imei"));
        assertFalse(normalized.contains("customer"));
        assertFalse(normalized.contains("payment"));
        assertFalse(normalized.contains("audit"));
    }

    @Test
    void authorizedMatchBindsInputsAndReturnsMinimalRecord() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1, UUID.class)).thenReturn(DEVICE_ID);
        when(resultSet.getObject(2, UUID.class)).thenReturn(PRODUCT_ID);
        when(resultSet.getObject(3, UUID.class)).thenReturn(WAREHOUSE_ID);
        when(resultSet.getString(4)).thenReturn("IN_STOCK");
        stubJdbcCallbacks(statement, resultSet);

        Optional<QrDeviceRecord> result = dataAccess.findByQrToken(QR_TOKEN, ACTOR_ID);

        assertEquals(Optional.of(new QrDeviceRecord(
                DEVICE_ID, PRODUCT_ID, WAREHOUSE_ID, "IN_STOCK")), result);
        verify(statement).setObject(1, QR_TOKEN);
        verify(statement).setObject(2, ACTOR_ID);
    }

    @Test
    void missingDeviceReturnsEmpty() throws Exception {
        assertNoVisibleRowReturnsEmpty();
    }

    @Test
    void unauthorizedOrCrossStoreDeviceReturnsEmptyWithoutDisclosure() throws Exception {
        assertNoVisibleRowReturnsEmpty();
    }

    @Test
    void propagatesJdbcFailureWithoutRetryOrFallback() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(jdbcTemplate.query(
                eq(FIND_BY_QR_TOKEN_SQL),
                ArgumentMatchers.<PreparedStatementSetter>any(),
                ArgumentMatchers.<ResultSetExtractor<Optional<QrDeviceRecord>>>any()))
                .thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> dataAccess.findByQrToken(QR_TOKEN, ACTOR_ID));

        assertSame(failure, thrown);
        verify(jdbcTemplate, times(1)).query(
                eq(FIND_BY_QR_TOKEN_SQL),
                ArgumentMatchers.<PreparedStatementSetter>any(),
                ArgumentMatchers.<ResultSetExtractor<Optional<QrDeviceRecord>>>any());
    }

    @Test
    void rejectsMissingInternalIdentifiersWithoutQueryingDatabase() {
        assertThrows(
                IllegalArgumentException.class,
                () -> dataAccess.findByQrToken(null, ACTOR_ID));
        assertThrows(
                IllegalArgumentException.class,
                () -> dataAccess.findByQrToken(QR_TOKEN, null));

        verifyNoInteractions(jdbcTemplate);
    }

    private void assertNoVisibleRowReturnsEmpty() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        stubJdbcCallbacks(statement, resultSet);

        assertEquals(Optional.empty(), dataAccess.findByQrToken(QR_TOKEN, ACTOR_ID));
    }

    private void stubJdbcCallbacks(
            PreparedStatement statement,
            ResultSet resultSet) {
        when(jdbcTemplate.query(
                eq(FIND_BY_QR_TOKEN_SQL),
                ArgumentMatchers.<PreparedStatementSetter>any(),
                ArgumentMatchers.<ResultSetExtractor<Optional<QrDeviceRecord>>>any()))
                .thenAnswer(invocation -> {
                    PreparedStatementSetter setter = invocation.getArgument(1);
                    ResultSetExtractor<Optional<QrDeviceRecord>> extractor =
                            invocation.getArgument(2);
                    setter.setValues(statement);
                    return extractor.extractData(resultSet);
                });
    }
}
