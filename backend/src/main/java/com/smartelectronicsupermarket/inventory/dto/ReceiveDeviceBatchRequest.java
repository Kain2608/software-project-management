package com.smartelectronicsupermarket.inventory.dto;

import java.util.List;

public record ReceiveDeviceBatchRequest(List<InventoryDeviceInput> devices) {
}
