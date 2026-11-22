package com.smartelectronicsupermarket.ai.summary;

import java.util.UUID;

/** Catalog facts retrieved from PostgreSQL for one product: the only knowledge the summarizer may use (RAG source). */
public record SpecProduct(
        UUID id, String sku, String name, String brand, String category, boolean active, String specificationsJson) {
}
