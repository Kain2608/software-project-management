package com.smartelectronicsupermarket.ai.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CrossSellRequest(
        @JsonProperty("store_id") String storeId,
        @JsonProperty("warehouse_id") String warehouseId,
        @JsonProperty("items") List<CartItemInput> items,
        @JsonProperty("sku_ids") List<String> skuIds,
        @JsonProperty("limit") Integer limit
) {
    public static final int DEFAULT_LIMIT = 4;
    public static final int MAX_LIMIT = 10;
    public static final int MAX_CART_SKUS = 50;

    public CrossSellRequest {
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("store_id cannot be blank");
        }
        if (warehouseId == null || warehouseId.isBlank()) {
            throw new IllegalArgumentException("warehouse_id cannot be blank");
        }
        boolean hasItems = items != null && !items.isEmpty();
        boolean hasSkus = skuIds != null && !skuIds.isEmpty();
        if (!hasItems && !hasSkus) {
            throw new IllegalArgumentException("Request must contain at least 1 item in 'items' or 'sku_ids'");
        }
        if (limit == null || limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must not exceed " + MAX_LIMIT);
        }
    }

    /** Normalised (trimmed, upper-case, de-duplicated) cart SKUs. */
    public List<String> getEffectiveSkuIds() {
        List<String> raw = new ArrayList<>();
        if (items != null && !items.isEmpty()) {
            for (CartItemInput item : items) {
                if (item != null && item.sku() != null) {
                    raw.add(item.sku());
                }
            }
        } else if (skuIds != null) {
            raw.addAll(skuIds);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String sku : raw) {
            if (sku != null && !sku.isBlank()) {
                unique.add(sku.strip().toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(unique);
    }
}
