package com.smartelectronicsupermarket.devicehistory.data;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeviceHistoryDataAccess implements DeviceHistoryDataAccess {
    static final String DEVICE_SQL = """
            SELECT d.id, d.product_id, p.sku, p.name, d.warehouse_id,
                   d.serial, d.imei, d.status
            FROM sasms.devices d
            JOIN sasms.products p ON p.id=d.product_id
            JOIN sasms.warehouses w ON w.id=d.warehouse_id
            WHERE d.id=CAST(? AS uuid)
              AND sasms.has_permission(CAST(? AS uuid),'inventory.read',w.store_id)=TRUE
            """;
    static final String EVENTS_SQL = """
            SELECT e.event_id,e.source,e.event_type,e.event_at,e.warehouse_id,e.order_id,e.state
            FROM (
              SELECT m.id event_id,'INVENTORY' source,m.kind event_type,m.created_at event_at,
                     m.warehouse_id,m.order_id,NULL::text state
              FROM sasms.inventory_movements m JOIN sasms.warehouses w ON w.id=m.warehouse_id
              WHERE m.device_id=CAST(? AS uuid)
                AND sasms.has_permission(CAST(? AS uuid),'inventory.read',w.store_id)=TRUE
              UNION ALL
              SELECT r.id,'RETURN','RETURN_'||r.status,r.created_at,NULL::uuid,r.order_id,r.status
              FROM sasms.returns r JOIN sasms.return_items ri ON ri.return_id=r.id
              JOIN sasms.sales_orders o ON o.id=r.order_id
              WHERE ri.device_id=CAST(? AS uuid)
                AND sasms.has_permission(CAST(? AS uuid),'sales.read',o.store_id)=TRUE
              UNION ALL
              SELECT d.id,'DELIVERY','DELIVERY_'||d.status,COALESCE(d.delivered_at,d.scheduled_at),
                     o.warehouse_id,d.order_id,d.status
              FROM sasms.deliveries d JOIN sasms.delivery_items di ON di.delivery_id=d.id
              JOIN sasms.sales_orders o ON o.id=d.order_id
              WHERE di.device_id=CAST(? AS uuid) AND COALESCE(d.delivered_at,d.scheduled_at) IS NOT NULL
                AND sasms.has_permission(CAST(? AS uuid),'sales.read',o.store_id)=TRUE
              UNION ALL
              SELECT wa.id,'WARRANTY','WARRANTY_'||wa.status,
                     wa.starts_on::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh',NULL::uuid,o.id,wa.status
              FROM sasms.warranties wa JOIN sasms.devices dev ON dev.id=wa.device_id
              JOIN sasms.sales_orders o ON o.id=dev.sold_order_id
              WHERE wa.device_id=CAST(? AS uuid)
                AND sasms.has_permission(CAST(? AS uuid),'warranty.manage',o.store_id)=TRUE
            ) e ORDER BY e.event_at DESC,e.event_id DESC LIMIT ? OFFSET ?
            """;

    private final JdbcTemplate jdbc;
    public JdbcDeviceHistoryDataAccess(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override public Optional<DeviceSnapshot> findDevice(UUID deviceId, UUID actorId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(DEVICE_SQL,(rs,n)->new DeviceSnapshot(
                    rs.getObject("id",UUID.class),rs.getObject("product_id",UUID.class),rs.getString("sku"),
                    rs.getString("name"),rs.getObject("warehouse_id",UUID.class),rs.getString("serial"),
                    rs.getString("imei"),rs.getString("status")),deviceId,actorId));
        } catch (EmptyResultDataAccessException exception) { return Optional.empty(); }
    }

    @Override public List<DeviceHistoryEvent> findEvents(UUID deviceId, UUID actorId, int limit, int offset) {
        return jdbc.query(EVENTS_SQL,(rs,n)->new DeviceHistoryEvent(
                rs.getObject("event_id",UUID.class),rs.getString("source"),rs.getString("event_type"),
                instant(rs.getTimestamp("event_at")),rs.getObject("warehouse_id",UUID.class),
                rs.getObject("order_id",UUID.class),rs.getString("state")),
                deviceId,actorId,deviceId,actorId,deviceId,actorId,deviceId,actorId,limit,offset);
    }
    private static java.time.Instant instant(Timestamp value){ return value==null?null:value.toInstant(); }
}
