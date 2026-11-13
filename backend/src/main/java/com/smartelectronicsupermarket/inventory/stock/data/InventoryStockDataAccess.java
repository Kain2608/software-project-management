package com.smartelectronicsupermarket.inventory.stock.data;

import java.util.List;
import java.util.UUID;

public interface InventoryStockDataAccess {

    List<InventoryStockRecord> listStock(
            UUID actorId,
            UUID warehouseId,
            String sku,
            int limit,
            int offset);
}
