package com.smartelectronicsupermarket.security.identity;

public record ExternalIdentity(String issuer, String subject) {

    public ExternalIdentity {
        requireText(issuer, "issuer");
        requireText(subject, "subject");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
