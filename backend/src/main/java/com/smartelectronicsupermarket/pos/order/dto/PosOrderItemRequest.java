package com.smartelectronicsupermarket.pos.order.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PosOrderItemRequest(
        @JsonProperty("product_id") UUID productId,
        Integer quantity) {
}
