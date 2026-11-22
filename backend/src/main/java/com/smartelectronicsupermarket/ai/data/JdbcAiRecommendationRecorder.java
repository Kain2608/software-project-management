package com.smartelectronicsupermarket.ai.data;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiRecommendationRecorder implements AiRecommendationRecorder {

    static final String INSERT_SQL = """
            INSERT INTO sasms.ai_recommendations
                (source_product_id, recommended_product_id, source, score, model_version, expires_at)
            VALUES (CAST(? AS uuid), CAST(? AS uuid), ?, ?, ?, now() + make_interval(mins => ?))
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAiRecommendationRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(List<Row> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, rows.size(), (ps, row) -> {
            ps.setString(1, row.sourceProductId().toString());
            ps.setString(2, row.recommendedProductId().toString());
            ps.setString(3, row.source());
            ps.setBigDecimal(4, row.score());
            ps.setString(5, row.modelVersion());
            ps.setInt(6, EXPIRY_MINUTES);
        });
    }
}
