package com.smartelectronicsupermarket.pos.selection.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SelectedDeviceResponse(
        @JsonProperty("device_id") UUID deviceId,
        String status) {
}
