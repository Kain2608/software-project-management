package com.smartelectronicsupermarket.pos.receipt.data;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceiptLineRecord(
        UUID productId,
        UUID deviceId,
        String serial,
        BigDecimal unitPrice) {
}
