package com.smartelectronicsupermarket.pos.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PosOrderItemResponse(
        @JsonProperty("order_item_id") UUID orderItemId,
        @JsonProperty("product_id") UUID productId,
        String sku,
        @JsonProperty("product_name") String productName,
        Integer quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("line_total") BigDecimal lineTotal) {
}
