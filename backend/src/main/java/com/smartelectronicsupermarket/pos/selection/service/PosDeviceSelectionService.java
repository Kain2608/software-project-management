package com.smartelectronicsupermarket.pos.selection.service;

import java.util.UUID;

import com.smartelectronicsupermarket.pos.selection.data.PosDeviceSelectionDataAccess;
import org.springframework.stereotype.Service;

@Service
public class PosDeviceSelectionService {

    private final PosDeviceSelectionDataAccess dataAccess;

    public PosDeviceSelectionService(PosDeviceSelectionDataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public UUID lockDevice(
            UUID storeId,
            UUID orderId,
            UUID orderItemId,
            String serial,
            UUID actorId) {
        requireValue(storeId, "storeId");
        requireValue(orderId, "orderId");
        requireValue(orderItemId, "orderItemId");
        requireValue(actorId, "actorId");
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException("serial is required");
        }
        return dataAccess.lockDevice(storeId, orderId, orderItemId, serial, actorId);
    }

    public boolean releaseDevice(UUID orderId, UUID deviceId, UUID actorId) {
        requireValue(orderId, "orderId");
        requireValue(deviceId, "deviceId");
        requireValue(actorId, "actorId");
        return dataAccess.releaseDevice(orderId, deviceId, actorId);
    }

    private static void requireValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
