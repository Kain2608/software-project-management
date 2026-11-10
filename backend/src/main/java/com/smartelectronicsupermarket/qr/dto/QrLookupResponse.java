package com.smartelectronicsupermarket.qr.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QrLookupResponse(
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("product_id") UUID productId,
        @JsonProperty("warehouse_id") UUID warehouseId,
        String status) {
}
