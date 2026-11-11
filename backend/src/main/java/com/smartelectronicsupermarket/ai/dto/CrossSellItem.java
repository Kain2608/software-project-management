package com.smartelectronicsupermarket.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrossSellItem(
        @JsonProperty("sku_id") String skuId,
        @JsonProperty("reason") String reason
) {
    public CrossSellItem {
        if (skuId == null || skuId.isBlank()) {
            throw new IllegalArgumentException("sku_id cannot be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
    }
}
