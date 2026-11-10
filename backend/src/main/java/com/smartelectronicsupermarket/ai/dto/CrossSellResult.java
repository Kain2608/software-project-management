package com.smartelectronicsupermarket.ai.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CrossSellResult(
        @JsonProperty("suggestions") List<CrossSellItem> suggestions,
        @JsonProperty("source") String source, // "AI", "CACHE", "RULE_FALLBACK" per D52
        @JsonProperty("took_ms") long tookMs
) {
    public CrossSellResult {
        if (suggestions == null) {
            suggestions = List.of();
        }
        if (source == null || (!source.equals("AI") && !source.equals("CACHE") && !source.equals("RULE_FALLBACK"))) {
            throw new IllegalArgumentException("source must be one of: AI, CACHE, RULE_FALLBACK");
        }
    }
}
