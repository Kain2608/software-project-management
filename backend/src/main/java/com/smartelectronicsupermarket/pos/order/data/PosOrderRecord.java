package com.smartelectronicsupermarket.pos.order.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PosOrderRecord(
        UUID id,
        String code,
        UUID idempotencyKey,
        UUID storeId,
        UUID warehouseId,
        UUID customerId,
        String channel,
        String status,
        BigDecimal totalAmount,
        UUID createdBy,
        Instant createdAt,
        Instant holdExpiresAt) {
}
