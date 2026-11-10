package com.smartelectronicsupermarket.ai.adapter;

import java.util.List;
import com.smartelectronicsupermarket.ai.dto.CrossSellItem;

public interface LlmAdapter {

    String getProviderName();

    List<CrossSellItem> generateCrossSell(List<String> skuIds, String storeId);

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
