package com.smartelectronicsupermarket.sms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

class SmsOutboxWorkerTest {
    private static final UUID EVENT = UUID.fromString("00000000-0000-4000-8000-000000000321");

    @Test void claimCommitsBeforeHttpAndFinishCommitsSeparately() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var manager = mock(PlatformTransactionManager.class);
        var claimed = new SimpleTransactionStatus();
        var finished = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus(), claimed, finished, new SimpleTransactionStatus());
        stubClaim(jdbc, "{\"message\":\"Warranty demo\"}");
        var adapter = mock(SmsAdapter.class);
        when(adapter.send(any())).thenAnswer(invocation -> {
            verify(manager).commit(claimed);
            verify(manager, never()).commit(finished);
            return new SmsResult(SmsResult.Status.ACCEPTED, "provider-123", "SMS_PROVIDER_ACCEPTED");
        });
        worker(jdbc, adapter, manager).poll();
        verify(jdbc).queryForObject(contains("finish_sms_notification"), eq(Object.class),
                eq(EVENT), eq(1), eq("ACCEPTED"), eq("provider-123"), eq("SMS_PROVIDER_ACCEPTED"));
        verify(manager).commit(finished);
        var definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager, times(4)).getTransaction(definitions.capture());
        assertEquals(TransactionDefinition.PROPAGATION_NOT_SUPPORTED, definitions.getAllValues().get(0).getPropagationBehavior());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definitions.getAllValues().get(1).getPropagationBehavior());
    }

    @Test void invalidOutboxPayloadDoesNotSendAndIsMarkedFailed() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var manager = transactionManager();
        stubClaim(jdbc, "{\"other\":\"no-message\"}");
        SmsAdapter adapter = mock(SmsAdapter.class);
        worker(jdbc, adapter, manager).poll();
        verifyNoInteractions(adapter);
        verify(jdbc).queryForObject(contains("finish_sms_notification"), eq(Object.class),
                eq(EVENT), eq(1), eq("FAILED"), isNull(), eq("SMS_INVALID_OUTBOX_PAYLOAD"));
    }

    @Test void adapterFailureBecomesUnknownWithoutRetry() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubClaim(jdbc, "{\"message\":\"Warranty demo\"}");
        SmsAdapter adapter = mock(SmsAdapter.class);
        when(adapter.send(any())).thenThrow(new IllegalStateException("PII secret"));
        worker(jdbc, adapter, transactionManager()).poll();
        verify(adapter, times(1)).send(any());
        verify(jdbc).queryForObject(contains("finish_sms_notification"), eq(Object.class),
                eq(EVENT), eq(1), eq("UNKNOWN"), isNull(), eq("SMS_WORKER_UNCERTAIN"));
    }

    @Test void mockStatusIsStoredAsMockAndNoClaimMeansNoSend() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubClaim(jdbc, "{\"message\":\"Warranty demo\"}");
        SmsAdapter adapter = mock(SmsAdapter.class);
        when(adapter.send(any())).thenReturn(new SmsResult(SmsResult.Status.MOCK_ACCEPTED, "mock-123", "SMS_MOCK_ONLY"));
        worker(jdbc, adapter, transactionManager()).poll();
        verify(jdbc).queryForObject(contains("finish_sms_notification"), eq(Object.class),
                eq(EVENT), eq(1), eq("MOCK_ACCEPTED"), eq("mock-123"), eq("SMS_MOCK_ONLY"));
        verify(adapter, times(1)).send(any());
    }

    @Test void pollIsBoundedAtTenEvents() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var row = row("{\"message\":\"Warranty demo\"}");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<Object>>getArgument(1).mapRow(row, 0)));
        SmsAdapter adapter = request -> new SmsResult(SmsResult.Status.MOCK_ACCEPTED, "mock-123", "SMS_MOCK_ONLY");
        worker(jdbc, adapter, transactionManager()).poll();
        verify(jdbc, times(10)).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        verify(jdbc, times(10)).queryForObject(contains("finish_sms_notification"), eq(Object.class),
                eq(EVENT), eq(1), eq("MOCK_ACCEPTED"), eq("mock-123"), eq("SMS_MOCK_ONLY"));
    }

    private static SmsOutboxWorker worker(JdbcTemplate jdbc, SmsAdapter adapter, PlatformTransactionManager manager) {
        return new SmsOutboxWorker(jdbc, adapter, JsonMapper.builder().build(), manager);
    }

    private static PlatformTransactionManager transactionManager() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        return manager;
    }

    private static void stubClaim(JdbcTemplate jdbc, String parameters) throws Exception {
        ResultSet row = row(parameters);
        AtomicBoolean first = new AtomicBoolean(true);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any())).thenAnswer(invocation ->
                first.getAndSet(false) ? List.of(invocation.<RowMapper<Object>>getArgument(1).mapRow(row, 0)) : List.of());
    }

    private static ResultSet row(String parameters) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("event_id", UUID.class)).thenReturn(EVENT);
        when(row.getString("destination")).thenReturn("+84901234567");
        when(row.getString("template_code")).thenReturn("warranty_activated");
        when(row.getString("parameters")).thenReturn(parameters);
        when(row.getInt("attempt")).thenReturn(1);
        return row;
    }
}
