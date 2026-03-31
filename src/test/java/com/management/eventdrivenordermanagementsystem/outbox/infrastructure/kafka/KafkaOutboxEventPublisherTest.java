package com.management.eventdrivenordermanagementsystem.outbox.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;
import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaOutboxEventPublisherTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaOutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new KafkaOutboxEventPublisher(
            kafkaTemplate,
            new ObjectMapper(),
            "order-events",
            Duration.ofSeconds(2)
        );
    }

    @Test
    void publishMapsOutboxRecordToKafkaMessage() {
        OutboxEventRecord event = new OutboxEventRecord(
            UUID.randomUUID(),
            "order-42",
            "ORDER",
            "ORDER_CREATED",
            "{\"orderId\":\"order-42\"}",
            "{\"correlationId\":\"corr-42\"}",
            OutboxEventStatus.PENDING,
            Instant.parse("2026-03-30T09:00:00Z"),
            null,
            0,
            null
        );

        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publish(event);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("order-events");
        assertThat(record.key()).isEqualTo("order-42");
        assertThat(record.value()).isEqualTo("{\"orderId\":\"order-42\"}");

        assertThat(headerValue(record, "eventId")).isEqualTo(event.id().toString());
        assertThat(headerValue(record, "eventType")).isEqualTo("ORDER_CREATED");
        assertThat(headerValue(record, "correlationId")).isEqualTo("corr-42");
    }

    private String headerValue(ProducerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        assertThat(header).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
