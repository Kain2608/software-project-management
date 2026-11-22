package com.smartelectronicsupermarket.ai.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JdbcAiRecommendationRecorderTest {

    @Test
    void insertOnlyTouchesNonPersonalColumnsAndMatchesTheGrantedColumnList() {
        String sql = JdbcAiRecommendationRecorder.INSERT_SQL;
        assertTrue(sql.contains("INSERT INTO sasms.ai_recommendations"));
        for (String column : new String[] {"source_product_id", "recommended_product_id", "source", "score",
                "model_version", "expires_at"}) {
            assertTrue(sql.contains(column), column);
        }
        assertFalse(sql.toLowerCase().contains("customer"));
        assertFalse(sql.toLowerCase().contains("actor"));
        assertTrue(sql.chars().filter(c -> c == '?').count() == 6);
    }
}
