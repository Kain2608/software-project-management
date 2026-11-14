package com.smartelectronicsupermarket.pos.receipt.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReceiptResponse(
        @JsonProperty("order_id") UUID orderId,
        List<ReceiptItemResponse> items,
        String currency,
        @JsonProperty("total_minor") long totalMinor,
        @JsonProperty("paid_at") OffsetDateTime paidAt,
        @JsonProperty("lookup_qr_token") String lookupQrToken) {
}
