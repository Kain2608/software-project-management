package com.smartelectronicsupermarket.ai.summary;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSpecSummaryDataAccess implements SpecSummaryDataAccess {

    static final String PRODUCT_SQL = """
            SELECT p.id, p.sku, p.name, b.name AS brand_name, c.name AS category_name,
                   p.is_active, p.specifications::text AS specifications
            FROM sasms.products p
            JOIN sasms.brands b ON b.id = p.brand_id
            JOIN sasms.categories c ON c.id = p.category_id
            WHERE p.id = CAST(? AS uuid)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSpecSummaryDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SpecProduct> findProduct(UUID productId) {
        return jdbcTemplate.query(
                PRODUCT_SQL,
                (rs, row) -> new SpecProduct(
                        rs.getObject("id", UUID.class),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("brand_name"),
                        rs.getString("category_name"),
                        rs.getBoolean("is_active"),
                        rs.getString("specifications")),
                productId).stream().findFirst();
    }
}
