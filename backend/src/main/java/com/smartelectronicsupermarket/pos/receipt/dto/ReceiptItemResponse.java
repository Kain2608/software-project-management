package com.smartelectronicsupermarket.pos.receipt.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReceiptItemResponse(
        @JsonProperty("sku_id") UUID productId,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("serial_masked") String serialMasked,
        @JsonProperty("price_minor") long priceMinor) {
}
