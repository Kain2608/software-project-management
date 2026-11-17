package com.smartelectronicsupermarket.pos.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PosOrderItemResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("product_id") UUID productId,
        String sku,
        @JsonProperty("product_name") String productName,
        Integer quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("line_total") BigDecimal lineTotal) {

    @JsonProperty("order_item_id")
    public UUID orderItemId() {
        return id;
    }
}
