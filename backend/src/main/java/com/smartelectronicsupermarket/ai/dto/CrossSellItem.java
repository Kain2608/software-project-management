package com.smartelectronicsupermarket.ai.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CrossSellItem(
        @JsonProperty("product_id") String productId,
        @JsonProperty("sku") String sku,
        @JsonProperty("name") String name,
        @JsonProperty("list_price") BigDecimal listPrice,
        @JsonProperty("available_quantity") int availableQuantity,
        @JsonProperty("is_serialized") boolean isSerialized,
        @JsonProperty("reason") String reason
) {
    public CrossSellItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku cannot be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
        if (productId == null || productId.isBlank()) {
            productId = java.util.UUID.nameUUIDFromBytes(sku.getBytes()).toString();
        }
        if (name == null || name.isBlank()) {
            name = "Phụ kiện " + sku;
        }
        if (listPrice == null || listPrice.compareTo(BigDecimal.ZERO) < 0) {
            listPrice = BigDecimal.ZERO;
        }
        if (availableQuantity < 0) {
            availableQuantity = 0;
        }
    }
}
