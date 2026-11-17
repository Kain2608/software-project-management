package com.smartelectronicsupermarket.ai.adapter;

import java.util.List;
import com.smartelectronicsupermarket.ai.dto.CrossSellCandidate;

public interface LlmAdapter {

    String getProviderName();

    /** Returns SKU + reason only. Price, stock and identifiers are resolved from the database. */
    List<CrossSellCandidate> generateCrossSell(List<String> skuIds, String storeId);

    LlmCallLog getLastCallLog();

    record LlmCallLog(
            String provider,
            String modelVersion,
            long durationMs,
            int promptTokens,
            int completionTokens,
            boolean success,
            String errorMessage
    ) {}
}
