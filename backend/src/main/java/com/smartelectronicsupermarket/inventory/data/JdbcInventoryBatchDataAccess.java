package com.smartelectronicsupermarket.inventory.data;

import java.sql.Array;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInventoryBatchDataAccess implements InventoryBatchDataAccess {

    private static final String RECEIVE_DEVICE_BATCH_SQL = """
            SELECT sasms.receive_device_batch(
                CAST(? AS uuid),
                CAST(? AS uuid),
                CAST(? AS jsonb),
                CAST(? AS uuid)
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcInventoryBatchDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID[] receiveDeviceBatch(
            UUID receiptItemId,
            UUID requestKey,
            String devicesJson,
            UUID actorId) {
        UUID[] deviceIds = jdbcTemplate.query(
                RECEIVE_DEVICE_BATCH_SQL,
                statement -> {
                    statement.setObject(1, receiptItemId);
                    statement.setObject(2, requestKey);
                    statement.setString(3, devicesJson);
                    statement.setObject(4, actorId);
                },
                resultSet -> {
                    if (!resultSet.next()) {
                        return null;
                    }

                    Array sqlArray = resultSet.getArray(1);
                    if (sqlArray == null) {
                        return null;
                    }

                    try {
                        Object value = sqlArray.getArray();
                        if (value instanceof UUID[] ids) {
                            return ids;
                        }
                        throw new DataRetrievalFailureException(
                                "receive_device_batch returned a non-UUID array");
                    } finally {
                        free(sqlArray);
                    }
                });

        if (deviceIds == null || deviceIds.length == 0) {
            throw new DataRetrievalFailureException(
                    "receive_device_batch returned no device identifiers");
        }
        return deviceIds;
    }

    private static void free(Array sqlArray) throws SQLException {
        sqlArray.free();
    }
}
