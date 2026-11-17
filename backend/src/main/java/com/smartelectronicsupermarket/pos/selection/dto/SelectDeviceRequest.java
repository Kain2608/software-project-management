package com.smartelectronicsupermarket.pos.selection.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SelectDeviceRequest(
        @JsonProperty("store_id") UUID storeId,
        String serial) {
}
