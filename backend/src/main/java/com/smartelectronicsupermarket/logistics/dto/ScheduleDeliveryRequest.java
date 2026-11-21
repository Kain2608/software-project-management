package com.smartelectronicsupermarket.logistics.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScheduleDeliveryRequest(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("technician_id") UUID technicianId,
        @JsonProperty("scheduled_start") Instant scheduledStart,
        @JsonProperty("scheduled_end") Instant scheduledEnd,
        String address) {
}
