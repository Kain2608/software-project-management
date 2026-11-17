package com.smartelectronicsupermarket.pos.order.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreatePosOrderRequest(
        @JsonProperty("store_id") UUID storeId,
        @JsonProperty("warehouse_id") UUID warehouseId,
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("idempotency_key") Object rawIdempotencyKey,
        List<PosOrderItemRequest> items) {

    public Object idempotencyKey() {
        return rawIdempotencyKey;
    }
}
