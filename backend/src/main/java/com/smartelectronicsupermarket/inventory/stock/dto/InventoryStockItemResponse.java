package com.smartelectronicsupermarket.inventory.stock.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InventoryStockItemResponse(
        @JsonProperty("sku_id") UUID productId,
        @JsonProperty("warehouse_id") UUID warehouseId,
        @JsonProperty("accounting_stock") long accountingStock,
        @JsonProperty("sellable_count") long sellableCount,
        @JsonProperty("reserved_count") long reservedCount) {
}
