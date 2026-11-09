package com.smartelectronicsupermarket.common.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiErrorResponse(
        String code,
        String message,
        String field,
        @JsonProperty("correlation_id")
        String correlationId,
        boolean retryable) {
}
