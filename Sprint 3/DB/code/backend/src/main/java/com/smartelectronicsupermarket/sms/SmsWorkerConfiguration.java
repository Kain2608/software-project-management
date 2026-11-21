package com.smartelectronicsupermarket.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sasms.sms.worker.enabled", havingValue = "true")
@EnableScheduling
public class SmsWorkerConfiguration {
    @Bean
    SmsOutboxWorker smsOutboxWorker(JdbcTemplate jdbc, SmsAdapter adapter, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager, SmsProperties properties) {
        if (properties.mode() == SmsProperties.Mode.DISABLED) {
            throw new IllegalStateException("SMS worker requires explicit MOCK or HTTP mode");
        }
        return new SmsOutboxWorker(jdbc, adapter, objectMapper, transactionManager);
    }
}
