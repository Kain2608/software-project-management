package com.smartelectronicsupermarket.catalog.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductMutationResponse(
        @JsonProperty("product_id") UUID productId,
        int revision,
        boolean active) {
}
