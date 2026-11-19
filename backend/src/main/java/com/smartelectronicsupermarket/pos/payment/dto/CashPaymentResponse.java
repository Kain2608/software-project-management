package com.smartelectronicsupermarket.pos.payment.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CashPaymentResponse(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("payment_id") UUID paymentId,
        @JsonProperty("status") String status,
        @JsonProperty("invoice_number") String invoiceNumber,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("paid_at") OffsetDateTime paidAt,
        @JsonProperty("payment_method") String paymentMethod) {
}
