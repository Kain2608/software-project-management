package com.smartelectronicsupermarket.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

public record CreateProductRequest(
        String sku,
        @JsonProperty("brand_id") UUID brandId,
        @JsonProperty("category_id") UUID categoryId,
        String name,
        JsonNode specifications,
        BigDecimal price,
        @JsonProperty("warranty_months") Integer warrantyMonths) {
}
