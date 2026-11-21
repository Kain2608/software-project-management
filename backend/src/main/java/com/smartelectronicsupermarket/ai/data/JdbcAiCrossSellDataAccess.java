package com.smartelectronicsupermarket.ai.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiCrossSellDataAccess implements AiCrossSellDataAccess {

    private static final String WAREHOUSE_IN_STORE_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM sasms.warehouses w
                WHERE w.id = CAST(? AS uuid) AND w.store_id = CAST(? AS uuid)
            )
            """;

    private static final String PRODUCT_IDS_SQL = """
            SELECT p.sku, p.id
            FROM sasms.products p
            WHERE p.sku = ANY (CAST(? AS text[]))
            """;

    private static final String SELLABLE_SQL = """
            SELECT p.id, p.sku, p.name, p.list_price, sa.available, c.name AS category_name
            FROM sasms.products p
            JOIN sasms.categories c ON c.id = p.category_id
            JOIN LATERAL (
                SELECT count(*) AS available
                FROM sasms.devices d
                WHERE d.product_id = p.id AND d.warehouse_id = CAST(? AS uuid)
                  AND d.status = 'IN_STOCK'
            ) sa ON sa.available > 0
            WHERE p.sku = ANY (CAST(? AS text[]))
              AND p.is_active = TRUE
            ORDER BY p.sku ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAiCrossSellDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean warehouseBelongsToStore(UUID warehouseId, UUID storeId) {
        Boolean exists = jdbcTemplate.queryForObject(
                WAREHOUSE_IN_STORE_SQL, Boolean.class, warehouseId, storeId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Map<String, UUID> findProductIdsBySku(Collection<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return Map.of();
        }
        Map<String, UUID> result = new HashMap<>();
        jdbcTemplate.query(
                PRODUCT_IDS_SQL,
                resultSet -> {
                    result.put(resultSet.getString("sku"), resultSet.getObject("id", UUID.class));
                },
                skuArrayLiteral(skus));
        return result;
    }

    @Override
    public List<VerifiedProduct> findSellableProducts(Collection<String> skus, UUID warehouseId) {
        if (skus == null || skus.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                SELLABLE_SQL,
                (resultSet, rowNumber) -> new VerifiedProduct(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("sku"),
                        resultSet.getString("name"),
                        resultSet.getBigDecimal("list_price"),
                        resultSet.getLong("available"),
                        resultSet.getString("category_name")),
                warehouseId,
                skuArrayLiteral(skus));
    }

    /** Postgres text[] literal; every element is quoted and escaped, so SKUs stay data. */
    private static String skuArrayLiteral(Collection<String> skus) {
        StringBuilder literal = new StringBuilder("{");
        boolean first = true;
        for (String sku : skus) {
            if (!first) {
                literal.append(',');
            }
            first = false;
            literal.append('"').append(sku.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return literal.append('}').toString();
    }
}
