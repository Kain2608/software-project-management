package com.smartelectronicsupermarket.ai.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CrossSellRequest(
        @JsonProperty("store_id") String storeId,
        @JsonProperty("warehouse_id") String warehouseId,
        @JsonProperty("items") List<CartItemInput> items,
        @JsonProperty("sku_ids") List<String> skuIds,
        @JsonProperty("limit") Integer limit
) {
    public CrossSellRequest {
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("store_id cannot be blank");
        }
        boolean hasItems = (items != null && !items.isEmpty());
        boolean hasSkus = (skuIds != null && !skuIds.isEmpty());
        if (!hasItems && !hasSkus) {
            throw new IllegalArgumentException("Request must contain at least 1 item in 'items' or 'sku_ids'");
        }
        if (limit == null || limit <= 0) {
            limit = 4;
        }
    }

    public List<String> getEffectiveSkuIds() {
        if (items != null && !items.isEmpty()) {
            return items.stream()
                    .map(CartItemInput::sku)
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
        }
        if (skuIds != null) {
            return skuIds;
        }
        return List.of();
    }
}
