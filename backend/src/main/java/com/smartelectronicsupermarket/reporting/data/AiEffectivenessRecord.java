package com.smartelectronicsupermarket.reporting.data;

import java.math.BigDecimal;

/**
 * PAID order lines of one store whose product had been recommended by the AI engine for another item of the same
 * order, after the order was opened and before it was paid. It is an attribution estimate, not a click/accept event.
 */
public record AiEffectivenessRecord(long attributedOrderCount, long attributedItemCount, BigDecimal attributedRevenue,
                                    long llmItemCount, long cacheItemCount, long ruleItemCount) {
    public static final AiEffectivenessRecord EMPTY =
            new AiEffectivenessRecord(0, 0, BigDecimal.ZERO, 0, 0, 0);
}
