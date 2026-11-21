package com.smartelectronicsupermarket.logistics.service;

import java.time.Instant;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.logistics.data.DeliveryRecord;
import com.smartelectronicsupermarket.logistics.data.LogisticsDataAccess;
import org.springframework.stereotype.Service;

@Service
public class LogisticsService {
    private final LogisticsDataAccess dataAccess;

    public LogisticsService(LogisticsDataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public UUID schedule(UUID orderId, UUID technicianId, Instant start, Instant end,
                         String address, UUID actorId) {
        require(orderId, "order_id"); require(technicianId, "technician_id");
        require(actorId, "actor"); require(start, "scheduled_start"); require(end, "scheduled_end");
        if (!end.isAfter(start)) throw new IllegalArgumentException("scheduled_end must be after scheduled_start");
        String normalizedAddress = requiredText(address, "address", 2000);
        return dataAccess.scheduleDelivery(orderId, technicianId, start, end, normalizedAddress, actorId);
    }

    public UUID recordResult(UUID deliveryId, String result, String evidenceReference, UUID actorId) {
        require(deliveryId, "delivery_id"); require(actorId, "actor");
        if (!("COMPLETED".equals(result) || "FAILED".equals(result))) {
            throw new IllegalArgumentException("result must be COMPLETED or FAILED");
        }
        return dataAccess.recordDeliveryResult(deliveryId, result,
                requiredText(evidenceReference, "evidence_reference", 2000), actorId);
    }

    public DeliveryRecord find(UUID deliveryId, UUID actorId) {
        require(deliveryId, "delivery_id"); require(actorId, "actor");
        return dataAccess.findById(deliveryId, actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));
    }

    private static <T> T require(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String requiredText(String value, String field, int max) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) {
            throw new IllegalArgumentException(field + " must contain 1.." + max + " characters");
        }
        return value.trim();
    }
}
