package com.smartelectronicsupermarket.inventory.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;

class JdbcInventoryBatchDataAccessTest {

    private static final String RECEIVE_DEVICE_BATCH_SQL = """
            SELECT sasms.receive_device_batch(
                CAST(? AS uuid),
                CAST(? AS uuid),
                CAST(? AS jsonb),
                CAST(? AS uuid)
            )
            """;

    private static final UUID RECEIPT_ITEM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID REQUEST_KEY =
            UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final String DEVICES_JSON = """
            [{"serial":"SERIAL-2","imei":null},{"serial":"SERIAL-1"}]
            """.strip();

    private JdbcTemplate jdbcTemplate;
    private JdbcInventoryBatchDataAccess dataAccess;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        dataAccess = new JdbcInventoryBatchDataAccess(jdbcTemplate);
    }

    @Test
    void bindsContractParametersAndReturnsIdsInDatabaseOrder() throws Exception {
        UUID[] databaseOrder = {
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                UUID.fromString("00000000-0000-0000-0000-000000000101")
        };
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Array sqlArray = mock(Array.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getArray(1)).thenReturn(sqlArray);
        when(sqlArray.getArray()).thenReturn(databaseOrder);
        stubJdbcCallbacks(statement, resultSet);

        UUID[] result = dataAccess.receiveDeviceBatch(
                RECEIPT_ITEM_ID, REQUEST_KEY, DEVICES_JSON, ACTOR_ID);

        assertArrayEquals(databaseOrder, result);
        verify(statement).setObject(1, RECEIPT_ITEM_ID);
        verify(statement).setObject(2, REQUEST_KEY);
        verify(statement).setString(3, DEVICES_JSON);
        verify(statement).setObject(4, ACTOR_ID);
        verify(sqlArray).free();
    }

    @Test
    void propagatesJdbcFailureWithoutRetryOrFallback() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(jdbcTemplate.query(
                eq(RECEIVE_DEVICE_BATCH_SQL),
                ArgumentMatchers.<PreparedStatementSetter>any(),
                ArgumentMatchers.<ResultSetExtractor<UUID[]>>any())).thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> dataAccess.receiveDeviceBatch(
                        RECEIPT_ITEM_ID, REQUEST_KEY, DEVICES_JSON, ACTOR_ID));

        assertSame(failure, thrown);
        verify(jdbcTemplate, times(1)).query(
                eq(RECEIVE_DEVICE_BATCH_SQL),
                ArgumentMatchers.<PreparedStatementSetter>any(),
                ArgumentMatchers.<ResultSetExtractor<UUID[]>>any());
    }

    @Test
    void rejectsNullSqlArrayInsteadOfReportingSuccess() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getArray(1)).thenReturn(null);
        stubJdbcCallbacks(statement, resultSet);

        assertThrows(
                DataRetrievalFailureException.class,
                () -> dataAccess.receiveDeviceBatch(
                        RECEIPT_ITEM_ID, REQUEST_KEY, DEVICES_JSON, ACTOR_ID));
    }

    @Test
    void rejectsEmptyUuidArrayAndFreesJdbcResource() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Array sqlArray = mock(Array.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getArray(1)).thenReturn(sqlArray);
        when(sqlArray.getArray()).thenReturn(new UUID[0]);
        stubJdbcCallbacks(statement, resultSet);

        assertThrows(
                DataRetrievalFailureException.class,
                () -> dataAccess.receiveDeviceBatch(
                        RECEIPT_ITEM_ID, REQUEST_KEY, DEVICES_JSON, ACTOR_ID));
        verify(sqlArray).free();
    }

    @Test
    void rejectsMissingResultRowInsteadOfReportingSuccess() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        stubJdbcCallbacks(statement, resultSet);

        assertThrows(
                DataRetrievalFailureException.class,
                () -> dataAccess.receiveDeviceBatch(
                        RECEIPT_ITEM_ID, REQUEST_KEY, DEVICES_JSON, ACTOR_ID));
    }

    private void stubJdbcCallbacks(
            PreparedStatement statement,
            ResultSet resultSet) {
        when(jdbcTemplate.query(
                eq(RECEIVE_DEVICE_BATCH_SQL),
                ArgumentMatchers.<PreparedStatementSetter>any(),
                ArgumentMatchers.<ResultSetExtractor<UUID[]>>any())).thenAnswer(invocation -> {
                    PreparedStatementSetter setter = invocation.getArgument(1);
                    ResultSetExtractor<UUID[]> extractor = invocation.getArgument(2);
                    setter.setValues(statement);
                    return extractor.extractData(resultSet);
                });
    }
}
