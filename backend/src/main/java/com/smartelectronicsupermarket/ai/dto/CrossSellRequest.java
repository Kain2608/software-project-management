package com.smartelectronicsupermarket.ai.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CrossSellRequest(
        @JsonProperty("sku_ids") List<String> skuIds,
        @JsonProperty("store_id") String storeId
) {
    public CrossSellRequest {
        if (skuIds == null || skuIds.isEmpty()) {
            throw new IllegalArgumentException("sku_ids must contain at least 1 SKU");
        }
    }
}
