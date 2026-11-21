package com.smartelectronicsupermarket.logistics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RecordDeliveryResultRequest(
        String result,
        @JsonProperty("evidence_reference") String evidenceReference) {
}
