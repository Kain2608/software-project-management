package com.smartelectronicsupermarket.ai.summary;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpecSummaryRequest(
        @JsonProperty("store_id") String storeId,
        @JsonProperty("product_id") String productId,
        @JsonProperty("locale") String locale) {

    public SpecSummaryRequest {
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("store_id cannot be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("product_id cannot be blank");
        }
        if (locale == null || locale.isBlank()) {
            locale = "vi";
        }
        if (!"vi".equals(locale)) {
            throw new IllegalArgumentException("only locale 'vi' is supported");
        }
    }
}
