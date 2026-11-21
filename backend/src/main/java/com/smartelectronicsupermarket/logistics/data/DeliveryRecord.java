package com.smartelectronicsupermarket.logistics.data;

import java.time.Instant;
import java.util.UUID;

public record DeliveryRecord(
        UUID deliveryId,
        UUID orderId,
        UUID technicianId,
        Instant scheduledStart,
        Instant scheduledEnd,
        String status,
        String assignmentStatus) {
}
