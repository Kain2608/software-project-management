package com.smartelectronicsupermarket.reporting.data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StoreDashboardDataAccess {
    StoreSummaryRecord summary(UUID storeId,Instant from,Instant to);
    List<ProductSalesRecord> topProducts(UUID storeId,Instant from,Instant to,int limit);
    /** S4-03 / FR-15: revenue attributable to AI recommendations in this store (paid_at half-open range). */
    AiEffectivenessRecord aiEffectiveness(UUID storeId,Instant from,Instant to);
}
