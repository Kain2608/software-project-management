package com.smartelectronicsupermarket.qr;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Parser for the draft internal QR payload proposed by DS1 and D35.
 */
@Component
public final class QrPayloadParser {

    private static final String DEVICE_PREFIX = "SASMS:DEVICE:";
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}"
                    + "-[0-9a-f]{4}-[0-9a-f]{12}");

    public UUID parse(String rawPayload) {
        if (rawPayload == null) {
            throw new IllegalArgumentException("QR payload is required");
        }
        if (rawPayload.isEmpty()) {
            throw new IllegalArgumentException("QR payload must not be empty");
        }
        if (!rawPayload.startsWith(DEVICE_PREFIX)) {
            throw new IllegalArgumentException("QR payload has an unsupported prefix");
        }

        String token = rawPayload.substring(DEVICE_PREFIX.length());
        if (token.isEmpty()) {
            throw new IllegalArgumentException("QR payload is missing the device token");
        }
        if (!CANONICAL_UUID.matcher(token).matches()) {
            throw new IllegalArgumentException(
                    "QR device token must use canonical UUID format");
        }

        return UUID.fromString(token);
    }
}
