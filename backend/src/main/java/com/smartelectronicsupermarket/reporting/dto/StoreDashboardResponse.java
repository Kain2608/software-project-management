package com.smartelectronicsupermarket.reporting.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartelectronicsupermarket.reporting.data.AiEffectivenessRecord;
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
        @JsonProperty("top_products") List<Product> topProducts,
        @JsonProperty("ai_effectiveness") AiEffectiveness aiEffectiveness) {
    public record Product(@JsonProperty("product_id") UUID productId,String sku,String name,long quantity,BigDecimal amount){}

    /**
     * state: ATTRIBUTION_ESTIMATE (there are matching sales), NO_ATTRIBUTED_SALES (none in range).
     * revenue_share_percent is null (shown as N/A) when gross revenue is zero, never a division by zero (AC-15-3).
     */
    public record AiEffectiveness(String state,
            @JsonProperty("attributed_order_count") long attributedOrderCount,
            @JsonProperty("attributed_item_count") long attributedItemCount,
            @JsonProperty("attributed_revenue") BigDecimal attributedRevenue,
            @JsonProperty("revenue_share_percent") BigDecimal revenueSharePercent,
            @JsonProperty("source_breakdown") Map<String,Long> sourceBreakdown){}

    public static AiEffectiveness effectiveness(AiEffectivenessRecord a,BigDecimal grossPaidRevenue){
        AiEffectivenessRecord r=a==null?AiEffectivenessRecord.EMPTY:a;
        BigDecimal share=grossPaidRevenue!=null&&grossPaidRevenue.signum()>0
                ?r.attributedRevenue().multiply(BigDecimal.valueOf(100)).divide(grossPaidRevenue,2,RoundingMode.HALF_UP):null;
        return new AiEffectiveness(r.attributedItemCount()>0?"ATTRIBUTION_ESTIMATE":"NO_ATTRIBUTED_SALES",
                r.attributedOrderCount(),r.attributedItemCount(),r.attributedRevenue(),share,
                Map.of("LLM",r.llmItemCount(),"CACHE",r.cacheItemCount(),"RULE",r.ruleItemCount()));
    }

    public static StoreDashboardResponse from(UUID store,Instant from,Instant to,StoreSummaryRecord s,
            List<ProductSalesRecord> p,AiEffectivenessRecord ai){
        return new StoreDashboardResponse(store,from,to,Instant.now(),s.grossPaidRevenue(),s.paidOrderCount(),
                s.availableDeviceCount(),s.receivedMovementCount(),s.soldMovementCount(),s.returnedMovementCount(),
                null,"N/A_NO_ACCEPTANCE_EVENTS",p.stream().map(x->new Product(x.productId(),x.sku(),x.name(),x.quantity(),x.amount())).toList(),
                effectiveness(ai,s.grossPaidRevenue()));
    }
}
