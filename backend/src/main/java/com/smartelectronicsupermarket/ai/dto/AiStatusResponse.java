package com.smartelectronicsupermarket.ai.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AiStatusResponse(
        @JsonProperty("status") String status,
        @JsonProperty("service_name") String serviceName,
        @JsonProperty("active_provider") String activeProvider,
        @JsonProperty("cache_engine") String cacheEngine,
        @JsonProperty("circuit_breaker") String circuitBreaker,
        @JsonProperty("timeout_limit_ms") long timeoutLimitMs,
        @JsonProperty("supported_categories") List<String> supportedCategories
) {}
