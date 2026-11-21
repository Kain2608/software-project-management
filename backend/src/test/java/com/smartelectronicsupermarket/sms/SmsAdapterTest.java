package com.smartelectronicsupermarket.sms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SmsAdapterTest {
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final SmsRequest REQUEST = new SmsRequest(UUID.fromString("00000000-0000-4000-8000-000000000321"),
            "+84901234567", "warranty_activated", "Warranty demo only");
    private static final SmsProperties PROPERTIES = new SmsProperties(SmsProperties.Mode.HTTP,
            URI.create("https://sms.example.test/send"), "test-key", "SASMS", null, null);

    @Test void defaultModeIsDisabledAndMockIsExplicitAndStable() {
        var config = new SmsConfiguration();
        assertEquals(SmsResult.Status.DISABLED, config.smsAdapter(
                new SmsProperties(null, null, null, null, null, null), JSON).send(REQUEST).status());
        SmsAdapter mock = config.smsAdapter(new SmsProperties(SmsProperties.Mode.MOCK, null, null, null, null, null), JSON);
        assertEquals(SmsResult.Status.MOCK_ACCEPTED, mock.send(REQUEST).status());
        assertEquals(mock.send(REQUEST), mock.send(REQUEST));
    }

    @Test void springBindsModeAndRejectsIncompleteHttpConfiguration() {
        var runner = new ApplicationContextRunner().withUserConfiguration(SmsConfiguration.class)
                .withBean(ObjectMapper.class, () -> JSON);
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(SmsResult.Status.DISABLED, context.getBean(SmsAdapter.class).send(REQUEST).status());
        });
        runner.withPropertyValues("sasms.sms.mode=MOCK").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(SmsResult.Status.MOCK_ACCEPTED, context.getBean(SmsAdapter.class).send(REQUEST).status());
        });
        runner.withPropertyValues("sasms.sms.mode=HTTP").run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test void configurationRejectsUnsafeUrisHeadersAndTimeouts() {
        for (String uri : List.of("http://sms.example.test/send", "https://key@sms.example.test/send",
                "https://sms.example.test/send?key=secret", "https://sms.example.test/send#key")) {
            assertThrows(IllegalArgumentException.class, () -> new SmsProperties(SmsProperties.Mode.HTTP,
                    URI.create(uri), "test-key", "SASMS", null, null));
        }
        assertThrows(IllegalArgumentException.class, () -> new SmsProperties(SmsProperties.Mode.HTTP,
                PROPERTIES.endpoint(), "key\r\nInjected: bad", "SASMS", null, null));
        assertThrows(IllegalArgumentException.class, () -> new SmsProperties(null, null, null, null,
                Duration.ZERO, null));
        assertThrows(IllegalArgumentException.class, () -> new SmsProperties(null, null, null, null,
                null, Duration.ofSeconds(31)));
    }

    @Test void invalidRequestFailsBeforeProviderAndToStringRedactsSensitiveFields() {
        assertThrows(IllegalArgumentException.class, () -> new SmsRequest(REQUEST.eventId(), "0901234567", "ok", "test"));
        assertThrows(IllegalArgumentException.class, () -> new SmsRequest(REQUEST.eventId(), REQUEST.destination(), "bad/id", "test"));
        assertThrows(IllegalArgumentException.class, () -> new SmsRequest(REQUEST.eventId(), REQUEST.destination(), "ok", " "));
        assertThrows(IllegalArgumentException.class, () -> new SmsRequest(REQUEST.eventId(), REQUEST.destination(), "ok", "x".repeat(1001)));
        assertFalse(REQUEST.toString().contains(REQUEST.destination()));
        assertFalse(REQUEST.toString().contains(REQUEST.message()));
        assertFalse(PROPERTIES.toString().contains("test-key"));
        assertFalse(new SmsResult(SmsResult.Status.ACCEPTED, REQUEST.destination(), "SAFE").toString().contains(REQUEST.destination()));
    }

    @Test void sendsExpectedGenericContractAndDistinguishesAcceptanceFromDelivery() throws Exception {
        HttpClient client = mock(HttpClient.class);
        respond(client, 202, "{\"status\":\"accepted\",\"messageId\":\"msg-123\"}");
        SmsResult result = new HttpSmsAdapter(PROPERTIES, client, JSON).send(REQUEST);
        assertEquals(SmsResult.Status.ACCEPTED, result.status());
        assertEquals("msg-123", result.providerMessageId());
        var sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(1)).send(sent.capture(), any());
        HttpRequest request = sent.getValue();
        assertEquals("Bearer test-key", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(REQUEST.eventId().toString(), request.headers().firstValue("Idempotency-Key").orElseThrow());
        assertEquals(Duration.ofSeconds(5), request.timeout().orElseThrow());
        var subscriber = HttpResponse.BodySubscribers.ofByteArray();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
            @Override public void onNext(ByteBuffer buffer) { subscriber.onNext(List.of(buffer)); }
            @Override public void onError(Throwable error) { subscriber.onError(error); }
            @Override public void onComplete() { subscriber.onComplete(); }
        });
        var body = JSON.readTree(subscriber.getBody().toCompletableFuture().get());
        assertEquals(REQUEST.destination(), body.path("destination").asText());
        assertEquals(REQUEST.message(), body.path("message").asText());
        assertEquals("SASMS", body.path("brandname").asText());
    }

    @Test void repeatedCallsPreserveIdempotencyKeyWithoutClaimingExactlyOnceDelivery() throws Exception {
        HttpClient client = mock(HttpClient.class);
        respond(client, 202, "{\"status\":\"accepted\",\"messageId\":\"msg-123\"}");
        var adapter = new HttpSmsAdapter(PROPERTIES, client, JSON);
        adapter.send(REQUEST);
        adapter.send(REQUEST);
        var sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(sent.capture(), any());
        assertEquals(sent.getAllValues().get(0).headers().firstValue("Idempotency-Key"),
                sent.getAllValues().get(1).headers().firstValue("Idempotency-Key"));
    }

    @Test void rejectsPermanentFailuresAndNeverReflectsProviderBody() throws Exception {
        for (int code : List.of(400, 401, 403, 404, 422)) {
            HttpClient client = mock(HttpClient.class);
            respond(client, code, "secret-test-key " + REQUEST.destination());
            SmsResult result = new HttpSmsAdapter(PROPERTIES, client, JSON).send(REQUEST);
            assertEquals(SmsResult.Status.REJECTED, result.status());
            assertNull(result.providerMessageId());
            assertFalse(result.toString().contains(REQUEST.destination()));
            verify(client, times(1)).send(any(), any());
        }
    }

    @Test void redirectsConflictRateLimitAndServerErrorsNeedReconciliation() throws Exception {
        for (int code : List.of(301, 302, 307, 409, 429, 500, 503)) {
            HttpClient client = mock(HttpClient.class);
            respond(client, code, "{}");
            assertEquals(SmsResult.Status.UNKNOWN, new HttpSmsAdapter(PROPERTIES, client, JSON).send(REQUEST).status());
            verify(client, times(1)).send(any(), any());
        }
    }

    @Test void malformedOrUnexpectedSuccessDoesNotClaimAcceptance() throws Exception {
        for (String body : List.of("not json", "{}", "null", "{\"status\":\"delivered\",\"messageId\":\"m1\"}",
                "{\"status\":\"accepted\",\"messageId\":\"bad\\nID\"}")) {
            HttpClient client = mock(HttpClient.class);
            respond(client, 200, body);
            assertEquals(SmsResult.Status.UNKNOWN, new HttpSmsAdapter(PROPERTIES, client, JSON).send(REQUEST).status());
        }
    }

    @Test void timeoutHasNoAutomaticRetryAndNoSensitiveExceptionText() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any())).thenThrow(new IOException("test-key " + REQUEST.destination()));
        SmsResult result = new HttpSmsAdapter(PROPERTIES, client, JSON).send(REQUEST);
        assertEquals(SmsResult.Status.UNKNOWN, result.status());
        assertFalse(result.toString().contains("test-key"));
        verify(client, times(1)).send(any(), any());
    }

    @Test void interruptedSendRestoresInterruptFlag() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any())).thenThrow(new InterruptedException());
        try {
            assertEquals(SmsResult.Status.UNKNOWN, new HttpSmsAdapter(PROPERTIES, client, JSON).send(REQUEST).status());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test void responseBodyIsBounded() {
        var subscriber = new HttpSmsAdapter.LimitedBodySubscriber();
        Flow.Subscription subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[16 * 1024 + 1])));
        verify(subscription).cancel();
        assertThrows(ExecutionException.class, () -> subscriber.getBody().toCompletableFuture().get());
    }

    @SuppressWarnings("unchecked")
    private static void respond(HttpClient client, int status, String body) throws Exception {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(client.<byte[]>send(any(), any())).thenReturn(response);
    }
}
