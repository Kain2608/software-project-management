package com.smartelectronicsupermarket.logistics.data;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLogisticsDataAccess implements LogisticsDataAccess {
    static final String SCHEDULE_SQL = """
            SELECT sasms.schedule_delivery(
                CAST(? AS uuid), CAST(? AS uuid), CAST(? AS timestamptz),
                CAST(? AS timestamptz), CAST(? AS text), CAST(? AS uuid))
            """;
    static final String RESULT_SQL = """
            SELECT sasms.record_delivery_result(
                CAST(? AS uuid), CAST(? AS text), CAST(? AS text), CAST(? AS uuid))
            """;
    static final String FIND_SQL = """
            SELECT d.id, d.order_id, a.technician_id, d.scheduled_at,
                   d.scheduled_end_at, d.status, a.status AS assignment_status
            FROM sasms.deliveries d
            JOIN sasms.sales_orders o ON o.id = d.order_id
            JOIN sasms.technician_assignments a ON a.delivery_id = d.id
            WHERE d.id = CAST(? AS uuid)
              AND (sasms.has_permission(CAST(? AS uuid), 'logistics.manage', o.store_id)
                   OR (a.technician_id = CAST(? AS uuid)
                       AND sasms.has_permission(CAST(? AS uuid), 'logistics.execute', o.store_id)))
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcLogisticsDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID scheduleDelivery(UUID orderId, UUID technicianId, Instant start, Instant end,
                                 String address, UUID actorId) {
        return jdbcTemplate.queryForObject(SCHEDULE_SQL, UUID.class, orderId, technicianId,
                Timestamp.from(start), Timestamp.from(end), address, actorId);
    }

    @Override
    public UUID recordDeliveryResult(UUID deliveryId, String result, String evidenceReference,
                                     UUID actorId) {
        return jdbcTemplate.queryForObject(RESULT_SQL, UUID.class, deliveryId, result,
                evidenceReference, actorId);
    }

    @Override
    public Optional<DeliveryRecord> findById(UUID deliveryId, UUID actorId) {
        try {
            DeliveryRecord record = jdbcTemplate.queryForObject(FIND_SQL, (rs, rowNum) ->
                    new DeliveryRecord(
                            rs.getObject("id", UUID.class),
                            rs.getObject("order_id", UUID.class),
                            rs.getObject("technician_id", UUID.class),
                            instant(rs.getTimestamp("scheduled_at")),
                            instant(rs.getTimestamp("scheduled_end_at")),
                            rs.getString("status"),
                            rs.getString("assignment_status")),
                    deliveryId, actorId, actorId, actorId);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
