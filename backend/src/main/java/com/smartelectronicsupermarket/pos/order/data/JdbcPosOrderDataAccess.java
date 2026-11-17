package com.smartelectronicsupermarket.pos.order.data;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPosOrderDataAccess implements PosOrderDataAccess {

    private static final String HAS_PERMISSION_SQL = """
            SELECT sasms.has_permission(
                CAST(? AS uuid),
                'sales.checkout',
                CAST(? AS uuid)
            )
            """;

    private static final String IS_WAREHOUSE_IN_STORE_SQL = """
            SELECT EXISTS(
                SELECT 1 FROM sasms.warehouses
                WHERE id = CAST(? AS uuid)
                  AND store_id = CAST(? AS uuid)
                  AND is_active = TRUE
            )
            """;

    private static final String FIND_PRODUCT_SQL = """
            SELECT id, sku, name, list_price, is_active
            FROM sasms.products
            WHERE id = CAST(? AS uuid)
            """;

    private static final String FIND_ORDER_BY_ID_SQL = """
            SELECT so.id, so.code, so.idempotency_key, so.store_id, so.warehouse_id, so.customer_id,
                   so.channel, so.status, so.total_amount, so.created_by, so.created_at,
                   to_jsonb(so)->>'hold_expires_at' AS hold_expires_at_text
            FROM sasms.sales_orders so
            WHERE so.id = CAST(? AS uuid)
            """;

    private static final String FIND_ORDER_BY_IDEMPOTENCY_KEY_SQL = """
            SELECT so.id, so.code, so.idempotency_key, so.store_id, so.warehouse_id, so.customer_id,
                   so.channel, so.status, so.total_amount, so.created_by, so.created_at,
                   to_jsonb(so)->>'hold_expires_at' AS hold_expires_at_text
            FROM sasms.sales_orders so
            WHERE so.idempotency_key = CAST(? AS uuid)
            """;

    private static final String FIND_ORDER_ITEMS_SQL = """
            SELECT i.id, i.order_id, i.product_id, p.sku, p.name, i.quantity, i.unit_price, i.line_total
            FROM sasms.sales_order_items i
            JOIN sasms.products p ON p.id = i.product_id
            WHERE i.order_id = CAST(? AS uuid)
            ORDER BY i.id ASC
            """;

    private static final String INSERT_ORDER_SQL = """
            INSERT INTO sasms.sales_orders (
                id, code, idempotency_key, store_id, warehouse_id, customer_id, channel, created_by
            ) VALUES (
                CAST(? AS uuid), CAST(? AS text), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), 'POS', CAST(? AS uuid)
            )
            """;

    private static final String INSERT_ORDER_ITEM_SQL = """
            INSERT INTO sasms.sales_order_items (
                id, order_id, product_id, quantity, unit_price
            ) VALUES (
                CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS integer), CAST(? AS numeric)
            )
            """;

    private static final String RESERVE_ORDER_SQL = """
            SELECT sasms.reserve_order(
                CAST(? AS uuid),
                CAST(? AS uuid)
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPosOrderDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasCheckoutPermission(UUID actorId, UUID storeId) {
        Boolean granted = jdbcTemplate.queryForObject(
                HAS_PERMISSION_SQL,
                Boolean.class,
                actorId,
                storeId);
        return Boolean.TRUE.equals(granted);
    }

    @Override
    public boolean isWarehouseInStore(UUID warehouseId, UUID storeId) {
        Boolean exists = jdbcTemplate.queryForObject(
                IS_WAREHOUSE_IN_STORE_SQL,
                Boolean.class,
                warehouseId,
                storeId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public CatalogProductRecord findActiveProduct(UUID productId) {
        try {
            return jdbcTemplate.queryForObject(
                    FIND_PRODUCT_SQL,
                    (rs, rowNum) -> mapProduct(rs),
                    productId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    public Optional<PosOrderRecord> findOrderById(UUID orderId) {
        try {
            PosOrderRecord record = jdbcTemplate.queryForObject(
                    FIND_ORDER_BY_ID_SQL,
                    (rs, rowNum) -> mapOrder(rs),
                    orderId);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PosOrderRecord> findOrderByIdempotencyKey(UUID idempotencyKey) {
        try {
            PosOrderRecord record = jdbcTemplate.queryForObject(
                    FIND_ORDER_BY_IDEMPOTENCY_KEY_SQL,
                    (rs, rowNum) -> mapOrder(rs),
                    idempotencyKey);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<PosOrderItemRecord> findOrderItems(UUID orderId) {
        return jdbcTemplate.query(
                FIND_ORDER_ITEMS_SQL,
                (rs, rowNum) -> mapOrderItem(rs),
                orderId);
    }

    @Override
    @Transactional
    public PosOrderRecord createOrder(
            UUID orderId,
            String code,
            UUID idempotencyKey,
            UUID storeId,
            UUID warehouseId,
            UUID customerId,
            UUID actorId,
            List<PosOrderItemRecord> items) {
        jdbcTemplate.update(
                INSERT_ORDER_SQL,
                orderId,
                code,
                idempotencyKey,
                storeId,
                warehouseId,
                customerId,
                actorId);

        for (PosOrderItemRecord item : items) {
            jdbcTemplate.update(
                    INSERT_ORDER_ITEM_SQL,
                    item.id(),
                    orderId,
                    item.productId(),
                    item.quantity(),
                    item.unitPrice());
        }

        return findOrderById(orderId).orElseThrow(() ->
                new IllegalStateException("Failed to retrieve created sales order: " + orderId));
    }

    @Override
    public BigDecimal reserveOrder(UUID orderId, UUID actorId) {
        return jdbcTemplate.queryForObject(
                RESERVE_ORDER_SQL,
                BigDecimal.class,
                orderId,
                actorId);
    }

    private static CatalogProductRecord mapProduct(ResultSet rs) throws SQLException {
        return new CatalogProductRecord(
                rs.getObject("id", UUID.class),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getBigDecimal("list_price"),
                rs.getBoolean("is_active"));
    }

    private static PosOrderRecord mapOrder(ResultSet rs) throws SQLException {
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Instant holdExpiresAt = null;
        try {
            String holdExpiresAtText = rs.getString("hold_expires_at_text");
            if (holdExpiresAtText != null && !holdExpiresAtText.isBlank()) {
                try {
                    holdExpiresAt = java.time.OffsetDateTime.parse(holdExpiresAtText).toInstant();
                } catch (Exception ex) {
                    try {
                        holdExpiresAt = Instant.parse(holdExpiresAtText);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (SQLException ignored) {
            try {
                Timestamp holdExpiresAtTs = rs.getTimestamp("hold_expires_at");
                if (holdExpiresAtTs != null) {
                    holdExpiresAt = holdExpiresAtTs.toInstant();
                }
            } catch (SQLException ignored2) {
            }
        }
        return new PosOrderRecord(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getObject("idempotency_key", UUID.class),
                rs.getObject("store_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                rs.getString("channel"),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getObject("created_by", UUID.class),
                createdAtTs != null ? createdAtTs.toInstant() : null,
                holdExpiresAt);
    }

    private static PosOrderItemRecord mapOrderItem(ResultSet rs) throws SQLException {
        return new PosOrderItemRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("line_total"));
    }
}
