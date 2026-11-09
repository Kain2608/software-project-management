package com.smartelectronicsupermarket.catalog.data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductCatalogDataAccess implements ProductCatalogDataAccess {

    private static final String CREATE_PRODUCT_SQL = """
            SELECT sasms.create_product(?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
            """;

    private static final String UPDATE_PRODUCT_SQL = """
            SELECT sasms.update_product(?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT p.id, p.sku, p.brand_id, p.category_id, p.name,
                   p.specifications::text, p.list_price, p.warranty_months,
                   p.is_active, p.revision, p.updated_at
            FROM sasms.products p
            WHERE p.id = CAST(? AS uuid)
            """;

    private static final String LIST_PRODUCTS_SQL = """
            SELECT p.id, p.sku, p.brand_id, p.category_id, p.name,
                   p.specifications::text, p.list_price, p.warranty_months,
                   p.is_active, p.revision, p.updated_at
            FROM sasms.products p
            WHERE (CAST(? AS boolean) IS NULL OR p.is_active = CAST(? AS boolean))
              AND (CAST(? AS text) IS NULL
                   OR p.sku ILIKE ('%' || CAST(? AS text) || '%')
                   OR p.name ILIKE ('%' || CAST(? AS text) || '%'))
            ORDER BY p.name ASC, p.id ASC
            LIMIT ? OFFSET ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcProductCatalogDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ProductCatalogRecord> findById(UUID productId) {
        requireValue(productId, "productId");
        List<ProductCatalogRecord> matches = jdbcTemplate.query(
                FIND_BY_ID_SQL,
                (resultSet, rowNumber) -> mapProduct(resultSet),
                productId);
        return matches.stream().findFirst();
    }

    @Override
    public List<ProductCatalogRecord> listProducts(
            String query,
            Boolean active,
            int limit,
            int offset) {
        return jdbcTemplate.query(
                LIST_PRODUCTS_SQL,
                (resultSet, rowNumber) -> mapProduct(resultSet),
                active,
                active,
                query,
                query,
                query,
                limit,
                offset);
    }

    @Override
    public UUID createProduct(
            UUID actorId,
            String sku,
            UUID brandId,
            UUID categoryId,
            String name,
            String specificationsJson,
            BigDecimal price,
            int warrantyMonths) {
        return jdbcTemplate.queryForObject(
                CREATE_PRODUCT_SQL,
                UUID.class,
                actorId,
                sku,
                brandId,
                categoryId,
                name,
                specificationsJson,
                price,
                warrantyMonths);
    }

    @Override
    public int updateProduct(
            UUID actorId,
            UUID productId,
            int expectedRevision,
            String name,
            String specificationsJson,
            BigDecimal price,
            int warrantyMonths,
            boolean active) {
        return jdbcTemplate.queryForObject(
                UPDATE_PRODUCT_SQL,
                Integer.class,
                actorId,
                productId,
                expectedRevision,
                name,
                specificationsJson,
                price,
                warrantyMonths,
                active);
    }

    private static ProductCatalogRecord mapProduct(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        return new ProductCatalogRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("sku"),
                resultSet.getObject("brand_id", UUID.class),
                resultSet.getObject("category_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("specifications"),
                resultSet.getBigDecimal("list_price"),
                resultSet.getInt("warranty_months"),
                resultSet.getBoolean("is_active"),
                resultSet.getInt("revision"),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class));
    }

    private static void requireValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
