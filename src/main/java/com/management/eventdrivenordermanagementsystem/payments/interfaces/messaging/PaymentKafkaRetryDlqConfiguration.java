package com.management.eventdrivenordermanagementsystem.payments.interfaces.messaging;

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
public class PaymentKafkaRetryDlqConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PaymentKafkaRetryDlqConfiguration.class);
    private static final String CONSUMER_NAME = "payment-authorization-requested-listener";

    @Bean("paymentAuthorizationKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> paymentAuthorizationKafkaListenerContainerFactory(
        ConsumerFactory<String, String> consumerFactory,
        KafkaTemplate<String, String> kafkaTemplate,
        MeterRegistry meterRegistry,
        @Value("${payment.kafka.retry.max-attempts:3}") int maxAttempts,
        @Value("${payment.kafka.retry.backoff-ms:1000}") long backoffMs,
        @Value("${payment.kafka.dlq.topic:order-events.payment.dlq}") String dlqTopic,
        @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(autoStartup);

        Counter retryCounter = meterRegistry.counter("payments.authorization.request.retry.attempt");
        Counter dlqCounter = meterRegistry.counter("payments.authorization.request.dlq");

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (consumerRecord, exception) -> new TopicPartition(dlqTopic, consumerRecord.partition())
        );

        ConsumerRecordRecoverer meterAwareRecoverer = (consumerRecord, exception) -> {
            dlqCounter.increment();
            log.error(
                "consumer_dead_lettered consumerName={} eventType={} eventId={} orderId={} retryCount={} failureReason={}",
                CONSUMER_NAME,
                header(consumerRecord, "eventType"),
                header(consumerRecord, "eventId"),
                orderId(consumerRecord),
                maxAttempts,
                exceptionName(exception)
            );
            recoverer.accept(consumerRecord, exception);
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            meterAwareRecoverer,
            new FixedBackOff(backoffMs, Math.max(0, maxAttempts - 1L))
        );
        errorHandler.addNotRetryableExceptions(NonRetryablePaymentMessageException.class);
        errorHandler.setRetryListeners((consumerRecord, exception, deliveryAttempt) -> {
            retryCounter.increment();
            log.warn(
                "consumer_retry_attempt consumerName={} eventType={} eventId={} orderId={} retryCount={} failureReason={}",
                CONSUMER_NAME,
                header(consumerRecord, "eventType"),
                header(consumerRecord, "eventId"),
                orderId(consumerRecord),
                deliveryAttempt,
                exceptionName(exception)
            );
        });

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    private static String orderId(ConsumerRecord<?, ?> consumerRecord) {
        return consumerRecord.key() != null ? consumerRecord.key().toString() : "unknown";
    }

    private static String header(ConsumerRecord<?, ?> consumerRecord, String key) {
        if (consumerRecord.headers() == null || consumerRecord.headers().lastHeader(key) == null) {
            return "unknown";
        }
        return new String(consumerRecord.headers().lastHeader(key).value(), StandardCharsets.UTF_8);
    }

    private static String exceptionName(Exception exception) {
        return exception == null ? "unknown" : exception.getClass().getSimpleName();
    }
}


