package com.smartelectronicsupermarket.qr.data;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcQrDeviceLookupDataAccess implements QrDeviceLookupDataAccess {

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

    private final JdbcTemplate jdbcTemplate;

    public JdbcQrDeviceLookupDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<QrDeviceRecord> findByQrToken(UUID qrToken, UUID actorId) {
        requireValue(qrToken, "qrToken");
        requireValue(actorId, "actorId");

        return jdbcTemplate.query(
                FIND_BY_QR_TOKEN_SQL,
                statement -> {
                    statement.setObject(1, qrToken);
                    statement.setObject(2, actorId);
                },
                resultSet -> {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    QrDeviceRecord record = new QrDeviceRecord(
                            resultSet.getObject(1, UUID.class),
                            resultSet.getObject(2, UUID.class),
                            resultSet.getObject(3, UUID.class),
                            resultSet.getString(4));

                    if (resultSet.next()) {
                        throw new IncorrectResultSizeDataAccessException(1, 2);
                    }
                    return Optional.of(record);
                });
    }

    private static void requireValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
