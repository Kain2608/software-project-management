package com.smartelectronicsupermarket.sms;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import tools.jackson.databind.ObjectMapper;

/** Generic gateway contract; a chosen Brandname vendor still needs its mapping. */
public final class HttpSmsAdapter implements SmsAdapter {
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private final SmsProperties properties;
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    HttpSmsAdapter(SmsProperties properties, HttpClient client, ObjectMapper objectMapper) {
        if (properties.mode() != SmsProperties.Mode.HTTP) {
            throw new IllegalArgumentException("HTTP SMS adapter requires HTTP mode");
        }
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public SmsResult send(SmsRequest request) {
        Objects.requireNonNull(request);
        String body = objectMapper.writeValueAsString(Map.of(
                "eventId", request.eventId().toString(), "destination", request.destination(),
                "templateId", request.templateId(), "message", request.message(),
                "brandname", properties.brandname()));
        HttpRequest httpRequest = HttpRequest.newBuilder(properties.endpoint())
                .timeout(properties.requestTimeout())
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("Idempotency-Key", request.eventId().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            // Exactly one attempt. Never retry an ambiguous outcome here.
            HttpResponse<byte[]> response = client.send(httpRequest, ignored -> new LimitedBodySubscriber());
            int status = response.statusCode();
            if (status == 200 || status == 202) {
                var json = objectMapper.readTree(response.body());
                String state = json.path("status").asText();
                String messageId = json.path("messageId").asText();
                if ("accepted".equals(state) && messageId.matches("[A-Za-z0-9._:-]{1,128}")) {
                    return new SmsResult(SmsResult.Status.ACCEPTED, messageId, "SMS_PROVIDER_ACCEPTED");
                }
                return unknown("SMS_UNRECOGNIZED_RESPONSE");
            }
            if (status == 400 || status == 401 || status == 403 || status == 404 || status == 422) {
                return new SmsResult(SmsResult.Status.REJECTED, null, "SMS_PROVIDER_REJECTED");
            }
            // Includes redirects, conflict, rate limit and server failures: reconcile first.
            return unknown("SMS_HTTP_UNCERTAIN");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unknown("SMS_INTERRUPTED");
        } catch (IOException | RuntimeException exception) {
            // Provider bodies and exception messages may contain secrets/PII. Do not log them.
            return unknown("SMS_TRANSPORT_OR_RESPONSE_ERROR");
        }
    }

    private static SmsResult unknown(String code) {
        return new SmsResult(SmsResult.Status.UNKNOWN, null, code);
    }

    /** Bound memory while retaining HttpRequest's overall timeout during body receipt. */
    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int bytes;
        private boolean finished;

        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        @Override public void onNext(List<ByteBuffer> chunks) {
            if (finished) return;
            for (ByteBuffer chunk : chunks) {
                if (chunk.remaining() > MAX_RESPONSE_BYTES - bytes) {
                    finished = true;
                    subscription.cancel();
                    delegate.onError(new IOException("SMS response exceeds size limit"));
                    return;
                }
                bytes += chunk.remaining();
            }
            delegate.onNext(chunks);
        }
        @Override public void onError(Throwable error) { finished = true; delegate.onError(error); }
        @Override public void onComplete() { if (!finished) { finished = true; delegate.onComplete(); } }
    }
}
