package com.smartelectronicsupermarket.ai.data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Persists what the engine recommended (D55 section 8.1) so the dashboard can measure AI effectiveness. */
public interface AiRecommendationRecorder {

    /** source is the table value: RULE, LLM or CACHE. */
    record Row(UUID sourceProductId, UUID recommendedProductId, String source, BigDecimal score, String modelVersion) {
    }

    /** Minutes a recommendation is considered current (audit expiry). */
    int EXPIRY_MINUTES = 60;

    void record(List<Row> rows);

    AiRecommendationRecorder NOOP = rows -> {
    };
}
