package com.smartelectronicsupermarket.logistics.data;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LogisticsDataAccess {
    UUID scheduleDelivery(UUID orderId, UUID technicianId, Instant start, Instant end,
                          String address, UUID actorId);

    UUID recordDeliveryResult(UUID deliveryId, String result, String evidenceReference,
                              UUID actorId);

    Optional<DeliveryRecord> findById(UUID deliveryId, UUID actorId);
}
