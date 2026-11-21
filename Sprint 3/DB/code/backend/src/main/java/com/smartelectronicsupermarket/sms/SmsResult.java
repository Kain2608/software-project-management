package com.smartelectronicsupermarket.sms;

/** ACCEPTED means provider acceptance only; no adapter result claims delivery. */
public record SmsResult(Status status, String providerMessageId, String code) {
    public enum Status { DISABLED, MOCK_ACCEPTED, ACCEPTED, REJECTED, UNKNOWN }

    @Override
    public String toString() {
        return "SmsResult[status=" + status + ", providerMessageId=[REDACTED], code=" + code + "]";
    }
}
