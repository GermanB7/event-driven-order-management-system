package com.management.eventdrivenordermanagementsystem.outbox.infrastructure.config;

import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxPublisherConfiguration {

    @Bean
    @ConditionalOnMissingBean(OutboxEventPublisher.class)
    OutboxEventPublisher noopOutboxEventPublisher() {
        return event -> {
            throw new IllegalStateException("No OutboxEventPublisher is configured");
        };
    }
}

