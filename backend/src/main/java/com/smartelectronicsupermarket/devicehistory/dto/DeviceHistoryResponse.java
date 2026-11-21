package com.smartelectronicsupermarket.devicehistory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartelectronicsupermarket.devicehistory.data.DeviceHistoryEvent;
import com.smartelectronicsupermarket.devicehistory.data.DeviceSnapshot;

public record DeviceHistoryResponse(
        @JsonProperty("device_id") UUID deviceId,@JsonProperty("product_id") UUID productId,
        String sku,@JsonProperty("product_name") String productName,
        @JsonProperty("warehouse_id") UUID warehouseId,String serial,String imei,String status,
        List<Event> events,int limit,int offset) {
    public record Event(@JsonProperty("event_id") UUID eventId,String source,String type,
                        @JsonProperty("occurred_at") Instant occurredAt,
                        @JsonProperty("warehouse_id") UUID warehouseId,
                        @JsonProperty("order_id") UUID orderId,String state) {}
    public static DeviceHistoryResponse from(DeviceSnapshot d,List<DeviceHistoryEvent> events,int limit,int offset){
        return new DeviceHistoryResponse(d.deviceId(),d.productId(),d.sku(),d.productName(),d.warehouseId(),
                d.serial(),d.imei(),d.status(),events.stream().map(e->new Event(e.eventId(),e.source(),e.type(),
                e.occurredAt(),e.warehouseId(),e.orderId(),e.state())).toList(),limit,offset);
    }
}
