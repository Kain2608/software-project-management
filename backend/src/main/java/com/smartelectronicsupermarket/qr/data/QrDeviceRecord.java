package com.smartelectronicsupermarket.qr.data;

import java.time.LocalDate;
import java.util.UUID;

public record QrDeviceRecord(
        UUID deviceId,
        UUID productId,
        UUID warehouseId,
        String status,
        String warrantyStatus,
        LocalDate warrantyStartsOn,
        LocalDate warrantyEndsOn,
        String policyName) {

    public QrDeviceRecord(UUID deviceId, UUID productId, UUID warehouseId, String status) {
        this(deviceId, productId, warehouseId, status, null, null, null, null);
    }
}
