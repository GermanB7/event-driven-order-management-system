package com.management.eventdrivenordermanagementsystem.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import static org.mockito.Mockito.mock;

@Configuration(proxyBeanMethods = false)
public class KafkaTestFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("unchecked")
    ConsumerFactory<String, String> consumerFactory() {
        return (ConsumerFactory<String, String>) mock(ConsumerFactory.class);
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("unchecked")
    ProducerFactory<String, String> producerFactory() {
        return (ProducerFactory<String, String>) mock(ProducerFactory.class);
    }

    @Bean
    @ConditionalOnMissingBean
    KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}

