package com.management.eventdrivenordermanagementsystem.shipping.interfaces.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;

@Configuration
public class ShipmentKafkaRetryDlqConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ShipmentKafkaRetryDlqConfiguration.class);
    private static final String CONSUMER_NAME = "shipping-preparation-requested-listener";

    @Bean("shipmentPreparationKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> shipmentPreparationKafkaListenerContainerFactory(
        ConsumerFactory<String, String> consumerFactory,
        KafkaTemplate<String, String> kafkaTemplate,
        MeterRegistry meterRegistry,
        @Value("${shipping.kafka.retry.max-attempts:3}") int maxAttempts,
        @Value("${shipping.kafka.retry.backoff-ms:1000}") long backoffMs,
        @Value("${shipping.kafka.dlq.topic:order-events.shipping.dlq}") String dlqTopic
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        Counter retryCounter = meterRegistry.counter("shipping.preparation.request.retry.attempt");
        Counter dlqCounter = meterRegistry.counter("shipping.preparation.request.dlq");

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> new TopicPartition(dlqTopic, record.partition())
        );

        ConsumerRecordRecoverer meterAwareRecoverer = (record, exception) -> {
            dlqCounter.increment();
            log.error(
                "consumer_dead_lettered consumerName={} eventType={} eventId={} orderId={} retryCount={} failureReason={}",
                CONSUMER_NAME,
                header(record, "eventType"),
                header(record, "eventId"),
                orderId(record),
                maxAttempts,
                exception.getClass().getSimpleName()
            );
            recoverer.accept(record, exception);
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            meterAwareRecoverer,
            new FixedBackOff(backoffMs, Math.max(0, maxAttempts - 1L))
        );
        errorHandler.addNotRetryableExceptions(NonRetryableShipmentMessageException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> {
            retryCounter.increment();
            log.warn(
                "consumer_retry_attempt consumerName={} eventType={} eventId={} orderId={} retryCount={} failureReason={}",
                CONSUMER_NAME,
                header(record, "eventType"),
                header(record, "eventId"),
                orderId(record),
                deliveryAttempt,
                exception.getClass().getSimpleName()
            );
        });

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    private static String orderId(ConsumerRecord<?, ?> record) {
        return record.key() != null ? record.key().toString() : "unknown";
    }

    private static String header(ConsumerRecord<?, ?> record, String key) {
        if (record.headers() == null || record.headers().lastHeader(key) == null) {
            return "unknown";
        }
        return new String(record.headers().lastHeader(key).value(), StandardCharsets.UTF_8);
    }
}


