package com.smartelectronicsupermarket.inventory.stock.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InventoryStockListResponse(
        List<InventoryStockItemResponse> items,
        @JsonProperty("snapshot_at") OffsetDateTime snapshotAt,
        @JsonProperty("next_cursor") String nextCursor) {
}
