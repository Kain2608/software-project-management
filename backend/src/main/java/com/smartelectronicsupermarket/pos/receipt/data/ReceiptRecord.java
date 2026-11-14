package com.smartelectronicsupermarket.pos.receipt.data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReceiptRecord(
        UUID orderId,
        String status,
        BigDecimal totalAmount,
        OffsetDateTime paidAt,
        List<ReceiptLineRecord> items) {
}
