package com.smartelectronicsupermarket.pos.payment.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CompleteCashPaymentRequest(
        @JsonProperty("amount") BigDecimal amount) {
}
