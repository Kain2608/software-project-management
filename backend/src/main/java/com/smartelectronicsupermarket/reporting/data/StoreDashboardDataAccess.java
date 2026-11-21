package com.smartelectronicsupermarket.reporting.data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StoreDashboardDataAccess {
    StoreSummaryRecord summary(UUID storeId,Instant from,Instant to);
    List<ProductSalesRecord> topProducts(UUID storeId,Instant from,Instant to,int limit);
}
