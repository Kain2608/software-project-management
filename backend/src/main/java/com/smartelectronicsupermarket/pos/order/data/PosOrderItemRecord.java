package com.smartelectronicsupermarket.pos.order.data;

import java.math.BigDecimal;
import java.util.UUID;

public record PosOrderItemRecord(
        UUID id,
        UUID orderId,
        UUID productId,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
