package com.smartelectronicsupermarket.pos.payment.data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExistingPaymentRecord(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        OffsetDateTime capturedAt,
        String method,
        String status) {
}
