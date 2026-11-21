package com.smartelectronicsupermarket.devicehistory.data;

import java.time.Instant;
import java.util.UUID;

public record DeviceHistoryEvent(UUID eventId, String source, String type, Instant occurredAt,
                                 UUID warehouseId, UUID orderId, String state) {
}
