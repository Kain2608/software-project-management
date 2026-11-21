package com.smartelectronicsupermarket.qr.dto;

import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record QrLookupResponse(
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("product_id") UUID productId,
        @JsonProperty("warehouse_id") UUID warehouseId,
        @JsonProperty("status") String status,
        @JsonProperty("warranty_status") String warrantyStatus,
        @JsonProperty("warranty_starts_on") LocalDate warrantyStartsOn,
        @JsonProperty("warranty_ends_on") LocalDate warrantyEndsOn,
        @JsonProperty("policy_name") String policyName) {

    public QrLookupResponse(UUID deviceId, UUID productId, UUID warehouseId, String status) {
        this(deviceId, productId, warehouseId, status, null, null, null, null);
    }
}
