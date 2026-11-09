package com.smartelectronicsupermarket.inventory.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReceiveDeviceBatchResponse(
        @JsonProperty("request_key") UUID requestKey,
        @JsonProperty("receipt_item_id") UUID receiptItemId,
        @JsonProperty("device_ids") List<UUID> deviceIds,
        @JsonProperty("received_count") int receivedCount,
        String status) {
}
