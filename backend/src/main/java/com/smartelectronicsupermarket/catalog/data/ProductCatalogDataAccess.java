package com.smartelectronicsupermarket.catalog.data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCatalogDataAccess {

    Optional<ProductCatalogRecord> findById(UUID productId);

    List<ProductCatalogRecord> listProducts(
            String query,
            Boolean active,
            int limit,
            int offset);

    UUID createProduct(
            UUID actorId,
            String sku,
            UUID brandId,
            UUID categoryId,
            String name,
            String specificationsJson,
            BigDecimal price,
            int warrantyMonths);

    int updateProduct(
            UUID actorId,
            UUID productId,
            int expectedRevision,
            String name,
            String specificationsJson,
            BigDecimal price,
            int warrantyMonths,
            boolean active);
}
