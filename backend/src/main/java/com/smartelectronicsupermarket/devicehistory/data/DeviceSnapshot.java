package com.smartelectronicsupermarket.devicehistory.data;

import java.util.UUID;

public record DeviceSnapshot(UUID deviceId, UUID productId, String sku, String productName,
                             UUID warehouseId, String serial, String imei, String status) {
}
