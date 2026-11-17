package com.smartelectronicsupermarket.ai.dto;

import java.util.Locale;

/**
 * Unverified suggestion from the LLM or rule engine. Carries only the SKU and the reason:
 * price, stock, name and UUID are never taken from here (BR-09) and are resolved from the database.
 */
public record CrossSellCandidate(String sku, String reason) {

    public CrossSellCandidate {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku cannot be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
        sku = sku.strip().toUpperCase(Locale.ROOT);
    }
}
