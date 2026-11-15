package com.smartelectronicsupermarket.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CartItemInput(
        @JsonProperty("product_id") String productId,
        @JsonProperty("sku") String sku
) {}
