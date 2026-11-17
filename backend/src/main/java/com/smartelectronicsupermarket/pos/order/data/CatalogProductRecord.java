package com.smartelectronicsupermarket.pos.order.data;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogProductRecord(
        UUID id,
        String sku,
        String name,
        BigDecimal listPrice,
        boolean isActive) {
}
