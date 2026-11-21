package com.smartelectronicsupermarket.sms;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** One bounded poll; recovery of PROCESSING/UNKNOWN events is manual reconciliation. */
public final class SmsOutboxWorker {
    private final JdbcTemplate jdbc;
    private final SmsAdapter adapter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate independentTransaction;
    private final TransactionTemplate outsideTransaction;

    public SmsOutboxWorker(JdbcTemplate jdbc, SmsAdapter adapter, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.adapter = adapter;
        this.objectMapper = objectMapper;
        independentTransaction = new TransactionTemplate(transactionManager);
        independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        independentTransaction.setTimeout(10);
        outsideTransaction = new TransactionTemplate(transactionManager);
        outsideTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Scheduled(fixedDelayString = "${sasms.sms.worker.poll-delay-ms:5000}")
    public void poll() {
        outsideTransaction.executeWithoutResult(ignored -> {
            for (int count = 0; count < 10; count++) {
                List<Pending> rows = independentTransaction.execute(transaction -> jdbc.query(
                        "SELECT event_id,destination,template_code,parameters,attempt FROM sasms.claim_sms_notification()",
                        (row, number) -> new Pending(row.getObject("event_id", UUID.class),
                                row.getString("destination"), row.getString("template_code"),
                                row.getString("parameters"), row.getInt("attempt"))));
                if (rows == null || rows.isEmpty()) return;
                Pending pending = rows.getFirst();
                SmsResult result;
                SmsRequest request = null;
                try {
                    var parameters = objectMapper.readTree(pending.parameters());
                    if (!parameters.path("message").isString()) {
                        throw new IllegalArgumentException("SMS message parameter is required");
                    }
                    request = new SmsRequest(pending.eventId(), pending.destination(),
                            pending.templateCode(), parameters.path("message").asText());
                } catch (RuntimeException exception) {
                    // Rejected locally, before any call to the provider.
                }
                if (request == null) {
                    result = new SmsResult(SmsResult.Status.REJECTED, null, "SMS_INVALID_OUTBOX_PAYLOAD");
                } else {
                    try {
                        result = adapter.send(request);
                        if (result == null) throw new IllegalStateException("SMS adapter returned no result");
                    } catch (RuntimeException exception) {
                        // A custom adapter may throw after sending. Preserve the uncertain outcome.
                        result = new SmsResult(SmsResult.Status.UNKNOWN, null, "SMS_WORKER_UNCERTAIN");
                    }
                }
                String status = switch (result.status()) {
                    case DISABLED, REJECTED -> "FAILED";
                    case MOCK_ACCEPTED -> "MOCK_ACCEPTED";
                    case ACCEPTED -> "ACCEPTED";
                    case UNKNOWN -> "UNKNOWN";
                };
                SmsResult finalResult = result;
                independentTransaction.executeWithoutResult(transaction -> jdbc.queryForObject(
                        "SELECT sasms.finish_sms_notification(?, ?, ?, ?, ?)", Object.class,
                        pending.eventId(), pending.attempt(), status,
                        finalResult.providerMessageId(), finalResult.code()));
            }
        });
    }

    record Pending(UUID eventId, String destination, String templateCode, String parameters, int attempt) {
        @Override public String toString() { return "Pending[eventId=" + eventId + ", payload=[REDACTED]]"; }
    }
}
