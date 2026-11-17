package com.smartelectronicsupermarket.pos.order.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartelectronicsupermarket.pos.order.controller.PosOrderController;

public record CreatePosOrderRequest(
        @JsonProperty("store_id") UUID storeId,
        @JsonProperty("warehouse_id") UUID warehouseId,
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("idempotency_key") Object rawIdempotencyKey,
        List<PosOrderItemRequest> items) {

    public UUID idempotencyKey() {
        if (rawIdempotencyKey == null) {
            return null;
        }
        if (rawIdempotencyKey instanceof UUID u) {
            return u;
        }
        return PosOrderController.parseOrDeriveIdempotencyKey(rawIdempotencyKey.toString());
    }
}
