package com.smartelectronicsupermarket.inventory.stock.data;

import java.util.UUID;

public record InventoryStockRecord(
        UUID productId,
        UUID warehouseId,
        long accountingStock,
        long sellableCount,
        long reservedCount) {
}
