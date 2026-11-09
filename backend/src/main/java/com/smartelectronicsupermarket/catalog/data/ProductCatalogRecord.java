package com.smartelectronicsupermarket.catalog.data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductCatalogRecord(
        UUID productId,
        String sku,
        UUID brandId,
        UUID categoryId,
        String name,
        String specificationsJson,
        BigDecimal price,
        int warrantyMonths,
        boolean active,
        int revision,
        OffsetDateTime updatedAt) {
}
