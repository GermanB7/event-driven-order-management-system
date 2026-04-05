package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

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
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;

@Configuration
public class InventoryKafkaRetryDlqConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InventoryKafkaRetryDlqConfiguration.class);

    @Bean("inventoryReservationKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> inventoryReservationKafkaListenerContainerFactory(
        ConsumerFactory<String, String> consumerFactory,
        KafkaTemplate<String, String> kafkaTemplate,
        MeterRegistry meterRegistry,
        @Value("${inventory.kafka.retry.max-attempts:3}") int maxAttempts,
        @Value("${inventory.kafka.retry.backoff-ms:1000}") long backoffMs,
        @Value("${inventory.kafka.dlq.reservation-topic:order-events.inventory.reservation.dlq}") String dlqTopic
    ) {
        return factory(
            consumerFactory,
            kafkaTemplate,
            meterRegistry,
            maxAttempts,
            backoffMs,
            dlqTopic,
            "inventory-reservation-requested-listener",
            "inventory.reservation.request.retry.attempt",
            "inventory.reservation.request.dlq"
        );
    }

    @Bean("inventoryReleaseKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> inventoryReleaseKafkaListenerContainerFactory(
        ConsumerFactory<String, String> consumerFactory,
        KafkaTemplate<String, String> kafkaTemplate,
        MeterRegistry meterRegistry,
        @Value("${inventory.kafka.retry.max-attempts:3}") int maxAttempts,
        @Value("${inventory.kafka.retry.backoff-ms:1000}") long backoffMs,
        @Value("${inventory.kafka.dlq.release-topic:order-events.inventory.release.dlq}") String dlqTopic
    ) {
        return factory(
            consumerFactory,
            kafkaTemplate,
            meterRegistry,
            maxAttempts,
            backoffMs,
            dlqTopic,
            "inventory-release-requested-listener",
            "inventory.release.request.retry.attempt",
            "inventory.release.request.dlq"
        );
    }

    private ConcurrentKafkaListenerContainerFactory<String, String> factory(
        ConsumerFactory<String, String> consumerFactory,
        KafkaTemplate<String, String> kafkaTemplate,
        MeterRegistry meterRegistry,
        int maxAttempts,
        long backoffMs,
        String dlqTopic,
        String consumerName,
        String retryMetric,
        String dlqMetric
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        Counter retryCounter = meterRegistry.counter(retryMetric);
        Counter dlqCounter = meterRegistry.counter(dlqMetric);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> new TopicPartition(dlqTopic, record.partition())
        );

        ConsumerRecordRecoverer meterAwareRecoverer = (record, exception) -> {
            dlqCounter.increment();
            log.error(
                "consumer_dead_lettered consumerName={} eventType={} eventId={} orderId={} retryCount={} failureReason={}",
                consumerName,
                header(record, "eventType"),
                header(record, "eventId"),
                orderId(record),
                maxAttempts,
                exceptionName(exception)
            );
            recoverer.accept(record, exception);
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            meterAwareRecoverer,
            new FixedBackOff(backoffMs, Math.max(0, maxAttempts - 1L))
        );
        errorHandler.addNotRetryableExceptions(NonRetryableInventoryMessageException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> {
            retryCounter.increment();
            log.warn(
                "consumer_retry_attempt consumerName={} eventType={} eventId={} orderId={} retryCount={} failureReason={}",
                consumerName,
                header(record, "eventType"),
                header(record, "eventId"),
                orderId(record),
                deliveryAttempt,
                exceptionName(exception)
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

    private static String exceptionName(Exception exception) {
        return exception == null ? "unknown" : exception.getClass().getSimpleName();
    }
}

