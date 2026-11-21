package com.smartelectronicsupermarket.reporting.data;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSalesRecord(UUID productId,String sku,String name,long quantity,BigDecimal amount) {}
