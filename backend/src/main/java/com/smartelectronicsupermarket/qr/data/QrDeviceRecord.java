package com.smartelectronicsupermarket.qr.data;

import java.util.UUID;

public record QrDeviceRecord(
        UUID deviceId,
        UUID productId,
        UUID warehouseId,
        String status) {
}
