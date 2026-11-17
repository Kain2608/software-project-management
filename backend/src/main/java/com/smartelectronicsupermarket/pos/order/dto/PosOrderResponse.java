package com.smartelectronicsupermarket.pos.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PosOrderResponse(
        @JsonProperty("order_id") UUID orderId,
        String code,
        @JsonProperty("idempotency_key") UUID idempotencyKey,
        @JsonProperty("store_id") UUID storeId,
        @JsonProperty("warehouse_id") UUID warehouseId,
        @JsonProperty("customer_id") UUID customerId,
        String channel,
        String status,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("created_by") UUID createdBy,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("hold_expires_at") Instant holdExpiresAt,
        List<PosOrderItemResponse> items) {
}
