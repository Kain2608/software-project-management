package com.smartelectronicsupermarket.catalog.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

public record UpdateProductRequest(
        @JsonProperty("expected_revision") Integer expectedRevision,
        String name,
        JsonNode specifications,
        BigDecimal price,
        @JsonProperty("warranty_months") Integer warrantyMonths,
        Boolean active) {
}
