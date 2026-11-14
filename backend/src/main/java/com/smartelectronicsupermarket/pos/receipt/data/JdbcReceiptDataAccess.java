package com.smartelectronicsupermarket.pos.receipt.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReceiptDataAccess implements ReceiptDataAccess {

    private static final String FIND_RECEIPT_SQL = """
            SELECT
                o.id AS order_id,
                o.status,
                o.total_amount,
                o.paid_at,
                i.product_id,
                od.device_id,
                d.serial,
                i.unit_price
            FROM sasms.sales_orders o
            LEFT JOIN sasms.sales_order_items i ON i.order_id = o.id
            LEFT JOIN sasms.order_devices od
              ON od.order_item_id = i.id AND od.active = TRUE
            LEFT JOIN sasms.devices d ON d.id = od.device_id
            WHERE o.id = CAST(? AS uuid)
              AND sasms.has_permission(
                    CAST(? AS uuid),
                    'sales.read',
                    o.store_id
                  ) = TRUE
            ORDER BY i.id ASC, od.device_id ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcReceiptDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ReceiptRecord> findByOrderId(UUID actorId, UUID orderId) {
        requireValue(actorId, "actorId");
        requireValue(orderId, "orderId");
        return jdbcTemplate.query(
                FIND_RECEIPT_SQL,
                statement -> {
                    statement.setObject(1, orderId);
                    statement.setObject(2, actorId);
                },
                resultSet -> {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    UUID foundOrderId = resultSet.getObject("order_id", UUID.class);
                    String status = resultSet.getString("status");
                    var total = resultSet.getBigDecimal("total_amount");
                    var paidAt = resultSet.getObject("paid_at", java.time.OffsetDateTime.class);
                    var items = new ArrayList<ReceiptLineRecord>();
                    do {
                        UUID productId = resultSet.getObject("product_id", UUID.class);
                        UUID deviceId = resultSet.getObject("device_id", UUID.class);
                        if (productId != null && deviceId != null) {
                            items.add(new ReceiptLineRecord(
                                    productId,
                                    deviceId,
                                    resultSet.getString("serial"),
                                    resultSet.getBigDecimal("unit_price")));
                        }
                        UUID rowOrderId = resultSet.getObject("order_id", UUID.class);
                        if (!foundOrderId.equals(rowOrderId)) {
                            throw new IncorrectResultSizeDataAccessException(1, 2);
                        }
                    } while (resultSet.next());
                    return Optional.of(new ReceiptRecord(
                            foundOrderId, status, total, paidAt, List.copyOf(items)));
                });
    }

    private static void requireValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
