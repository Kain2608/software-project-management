package com.smartelectronicsupermarket.catalog.dto;

import java.util.List;

public record ProductListResponse(
        List<ProductResponse> items,
        int limit,
        int offset,
        int count) {
}
