package com.smartelectronicsupermarket.pos.selection.data;

import java.util.UUID;

public interface PosDeviceSelectionDataAccess {

    UUID lockDevice(
            UUID storeId,
            UUID orderId,
            UUID orderItemId,
            String serial,
            UUID actorId);

    boolean releaseDevice(UUID orderId, UUID deviceId, UUID actorId);
}
