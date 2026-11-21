package com.smartelectronicsupermarket.sms;

/**
 * Internal integration boundary, never a browser-facing send API. Call after the
 * business transaction commits. Durable claiming/retry/reconciliation belongs
 * to the outbox worker; an HTTP timeout must not create a second event ID.
 */
@FunctionalInterface
public interface SmsAdapter {
    SmsResult send(SmsRequest request);
}
