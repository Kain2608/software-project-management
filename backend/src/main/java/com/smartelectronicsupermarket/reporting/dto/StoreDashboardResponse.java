package com.smartelectronicsupermarket.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartelectronicsupermarket.reporting.data.ProductSalesRecord;
import com.smartelectronicsupermarket.reporting.data.StoreSummaryRecord;

public record StoreDashboardResponse(@JsonProperty("store_id") UUID storeId,Instant from,Instant to,
        @JsonProperty("generated_at") Instant generatedAt,
        @JsonProperty("gross_paid_revenue") BigDecimal grossPaidRevenue,
        @JsonProperty("paid_order_count") long paidOrderCount,
        @JsonProperty("available_device_count") long availableDeviceCount,
        @JsonProperty("received_movement_count") long receivedMovementCount,
        @JsonProperty("sold_movement_count") long soldMovementCount,
        @JsonProperty("returned_movement_count") long returnedMovementCount,
        @JsonProperty("ai_acceptance_rate") BigDecimal aiAcceptanceRate,
        @JsonProperty("ai_acceptance_state") String aiAcceptanceState,
        @JsonProperty("top_products") List<Product> topProducts) {
    public record Product(@JsonProperty("product_id") UUID productId,String sku,String name,long quantity,BigDecimal amount){}
    public static StoreDashboardResponse from(UUID store,Instant from,Instant to,StoreSummaryRecord s,List<ProductSalesRecord> p){
        return new StoreDashboardResponse(store,from,to,Instant.now(),s.grossPaidRevenue(),s.paidOrderCount(),
                s.availableDeviceCount(),s.receivedMovementCount(),s.soldMovementCount(),s.returnedMovementCount(),
                null,"N/A_NO_ACCEPTANCE_EVENTS",p.stream().map(x->new Product(x.productId(),x.sku(),x.name(),x.quantity(),x.amount())).toList());
    }
}
