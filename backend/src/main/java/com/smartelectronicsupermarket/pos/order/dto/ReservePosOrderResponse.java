package com.smartelectronicsupermarket.pos.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReservePosOrderResponse(
        @JsonProperty("order_id") UUID orderId,
        String status,
        @JsonProperty("total_amount") BigDecimal totalAmount) {
}
