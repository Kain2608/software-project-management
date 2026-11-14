package com.smartelectronicsupermarket.inventory.stock.data;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInventoryStockDataAccess implements InventoryStockDataAccess {

    private static final String LIST_STOCK_SQL = """
            SELECT
                sa.product_id,
                sa.warehouse_id,
                sa.available + sa.reserved AS accounting_stock,
                sa.available AS sellable_count,
                sa.reserved AS reserved_count
            FROM sasms.stock_availability sa
            JOIN sasms.warehouses w ON w.id = sa.warehouse_id
            JOIN sasms.products p ON p.id = sa.product_id
            WHERE sasms.has_permission(
                    CAST(? AS uuid),
                    'inventory.read',
                    w.store_id
                  ) = TRUE
              AND (CAST(? AS uuid) IS NULL OR sa.warehouse_id = CAST(? AS uuid))
              AND (CAST(? AS text) IS NULL OR p.sku ILIKE ('%' || CAST(? AS text) || '%'))
            ORDER BY p.sku ASC, sa.warehouse_id ASC
            LIMIT ? OFFSET ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcInventoryStockDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<InventoryStockRecord> listStock(
            UUID actorId,
            UUID warehouseId,
            String sku,
            int limit,
            int offset) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        return jdbcTemplate.query(
                LIST_STOCK_SQL,
                (resultSet, rowNumber) -> new InventoryStockRecord(
                        resultSet.getObject("product_id", UUID.class),
                        resultSet.getObject("warehouse_id", UUID.class),
                        resultSet.getLong("accounting_stock"),
                        resultSet.getLong("sellable_count"),
                        resultSet.getLong("reserved_count")),
                actorId,
                warehouseId,
                warehouseId,
                sku,
                sku,
                limit,
                offset);
    }
}
