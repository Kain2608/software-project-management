package com.smartelectronicsupermarket.ai.dto;

import java.math.BigDecimal;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Suggestion returned to POS. Every field except {@code reason} comes from the database. */
public record CrossSellItem(
        @JsonProperty("product_id") UUID productId,
        @JsonProperty("sku") String sku,
        @JsonProperty("name") String name,
        @JsonProperty("list_price") BigDecimal listPrice,
        @JsonProperty("available_quantity") long availableQuantity,
        @JsonProperty("is_serialized") boolean isSerialized,
        @JsonProperty("reason") String reason
) {
    public CrossSellItem {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (sku == null || sku.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("sku and name are required");
        }
        if (listPrice == null || listPrice.signum() < 0) {
            throw new IllegalArgumentException("listPrice must be non-negative");
        }
        if (availableQuantity < 0) {
            throw new IllegalArgumentException("availableQuantity must be non-negative");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
    }
}
