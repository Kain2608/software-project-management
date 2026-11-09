package com.smartelectronicsupermarket.inventory.data;

import java.util.UUID;

public interface InventoryBatchDataAccess {

    UUID[] receiveDeviceBatch(
            UUID receiptItemId,
            UUID requestKey,
            String devicesJson,
            UUID actorId);
}
