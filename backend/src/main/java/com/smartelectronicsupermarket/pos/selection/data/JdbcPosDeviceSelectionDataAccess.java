package com.smartelectronicsupermarket.pos.selection.data;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPosDeviceSelectionDataAccess implements PosDeviceSelectionDataAccess {

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

    private final JdbcTemplate jdbcTemplate;

    public JdbcPosDeviceSelectionDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID lockDevice(
            UUID storeId,
            UUID orderId,
            UUID orderItemId,
            String serial,
            UUID actorId) {
        return jdbcTemplate.queryForObject(
                LOCK_DEVICE_SQL,
                UUID.class,
                storeId,
                orderId,
                orderItemId,
                serial,
                actorId);
    }

    @Override
    public boolean releaseDevice(UUID orderId, UUID deviceId, UUID actorId) {
        Boolean released = jdbcTemplate.query(
                RELEASE_DEVICE_SQL,
                statement -> {
                    statement.setObject(1, orderId);
                    statement.setObject(2, deviceId);
                    statement.setObject(3, actorId);
                    statement.setObject(4, orderId);
                    statement.setObject(5, actorId);
                },
                resultSet -> resultSet.next() ? resultSet.getObject(1, Boolean.class) : null);
        return Boolean.TRUE.equals(released);
    }
}
