package com.smartelectronicsupermarket.ai.data;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authoritative database reads for AI cross-sell (D52 v2 section 2.3). */
public interface AiCrossSellDataAccess {

    /** True only when the warehouse exists and belongs to the given store. */
    boolean warehouseBelongsToStore(UUID warehouseId, UUID storeId);

    /** Maps each existing SKU (upper-case) to its product id; unknown SKUs are absent. */
    Map<String, UUID> findProductIdsBySku(Collection<String> skus);

    /** Active products among {@code skus} with sellable stock (> 0) in the warehouse. */
    List<VerifiedProduct> findSellableProducts(Collection<String> skus, UUID warehouseId);

    record VerifiedProduct(
            UUID productId,
            String sku,
            String name,
            BigDecimal listPrice,
            long availableQuantity,
            String categoryName) {
    }
}
