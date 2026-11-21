package com.smartelectronicsupermarket.ai.dto;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AiEventRequest(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("sku_ids") List<String> skuIds,
        @JsonProperty("sku_id") String skuId,
        @JsonProperty("store_id") UUID storeId,
        @JsonProperty("warehouse_id") UUID warehouseId,
        @JsonProperty("source") String source
) {}
