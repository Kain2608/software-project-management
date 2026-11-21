package com.smartelectronicsupermarket.sms;

import java.util.Objects;
import java.util.UUID;

/** Trusted-worker input. The event ID must come from the committed outbox row. */
public record SmsRequest(UUID eventId, String destination, String templateId, String message) {
    public SmsRequest {
        Objects.requireNonNull(eventId, "SMS event ID is required");
        if (destination == null || !destination.matches("\\+[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("SMS destination must use E.164 format");
        }
        if (templateId == null || !templateId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("SMS template ID is invalid");
        }
        if (message == null || message.isBlank() || message.length() > 1000 || message.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("SMS message must contain 1 to 1000 characters");
        }
    }

    @Override
    public String toString() {
        return "SmsRequest[eventId=" + eventId + ", destination=[REDACTED], templateId=[REDACTED], message=[REDACTED]]";
    }
}
