package com.smartelectronicsupermarket.catalog.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

public record ProductResponse(
        @JsonProperty("product_id") UUID productId,
        String sku,
        @JsonProperty("brand_id") UUID brandId,
        @JsonProperty("category_id") UUID categoryId,
        String name,
        JsonNode specifications,
        BigDecimal price,
        @JsonProperty("warranty_months") int warrantyMonths,
        boolean active,
        int revision,
        @JsonProperty("updated_at") OffsetDateTime updatedAt) {
}
