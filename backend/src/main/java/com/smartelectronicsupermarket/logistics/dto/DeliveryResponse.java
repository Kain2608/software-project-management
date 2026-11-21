package com.smartelectronicsupermarket.logistics.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartelectronicsupermarket.logistics.data.DeliveryRecord;

public record DeliveryResponse(
        @JsonProperty("delivery_id") UUID deliveryId,
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("technician_id") UUID technicianId,
        @JsonProperty("scheduled_start") Instant scheduledStart,
        @JsonProperty("scheduled_end") Instant scheduledEnd,
        String status,
        @JsonProperty("assignment_status") String assignmentStatus) {
    public static DeliveryResponse from(DeliveryRecord value) {
        return new DeliveryResponse(value.deliveryId(), value.orderId(), value.technicianId(),
                value.scheduledStart(), value.scheduledEnd(), value.status(), value.assignmentStatus());
    }
}
