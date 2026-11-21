package com.smartelectronicsupermarket.sms;

import java.net.http.HttpClient;
import java.util.Objects;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfiguration {
    @Bean
    SmsAdapter smsAdapter(SmsProperties properties, ObjectMapper objectMapper) {
        return switch (properties.mode()) {
            case DISABLED -> request -> {
                Objects.requireNonNull(request);
                return new SmsResult(SmsResult.Status.DISABLED, null, "SMS_DISABLED");
            };
            case MOCK -> request -> {
                Objects.requireNonNull(request);
                return new SmsResult(SmsResult.Status.MOCK_ACCEPTED, "mock-" + request.eventId(), "SMS_MOCK_ONLY");
            };
            case HTTP -> new HttpSmsAdapter(properties, HttpClient.newBuilder()
                    .connectTimeout(properties.connectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).build(), objectMapper);
        };
    }
}
