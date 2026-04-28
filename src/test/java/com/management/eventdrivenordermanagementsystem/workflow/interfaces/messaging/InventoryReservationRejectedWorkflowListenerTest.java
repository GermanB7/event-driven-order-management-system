package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.messaging.infrastructure.persistence.JdbcProcessedMessageStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.TransientDataAccessResourceException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReservationRejectedWorkflowListenerTest {

    private OutboxEventWriter outboxEventWriter;
    private JdbcProcessedMessageStore processedMessageStore;
    private InventoryReservationRejectedWorkflowListener listener;

    @BeforeEach
    void setUp() {
        outboxEventWriter = mock(OutboxEventWriter.class);
        processedMessageStore = mock(JdbcProcessedMessageStore.class);
        listener = new InventoryReservationRejectedWorkflowListener(
            new ObjectMapper(),
            outboxEventWriter,
            new WorkflowConsumerFailureClassifier(),
            processedMessageStore,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void inventoryReservationRejectedEmitsOrderCancelledEvent() {
        ConsumerRecord<String, String> consumerRecord = inventoryRejectedRecord("""
            {
              "orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b",
              "workflowId": "wf-900",
              "correlationId": "corr-900",
              "reason": "INSUFFICIENT_STOCK",
              "failedSku": "SKU-1"
            }
            """);

        when(processedMessageStore.markProcessed(any(), any(), any(Instant.class))).thenReturn(true);

        listener.onMessage(consumerRecord);

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(captor.capture());

        EventEnvelope emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo(EventType.ORDER_CANCELLED);
        assertThat(emitted.aggregateId()).isEqualTo("8f3eec6f-c5d0-4d4b-9c44-a718ee05553b");
        assertThat(emitted.workflowId()).isEqualTo("wf-900");
        assertThat(emitted.correlationId()).isEqualTo("corr-900");
        assertThat(emitted.causationId()).isEqualTo("8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b");
        assertThat(emitted.payload().path("orderStatus").asText()).isEqualTo("CANCELLED");
        assertThat(emitted.payload().path("reason").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(emitted.payload().path("failedSku").asText()).isEqualTo("SKU-1");
    }

    @Test
    void duplicateInventoryReservationRejectedMessageDoesNotEmitOrderCancelledAgain() {
        ConsumerRecord<String, String> consumerRecord = inventoryRejectedRecord("""
            {
              "orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b",
              "reason": "INSUFFICIENT_STOCK"
            }
            """);

        when(processedMessageStore.markProcessed(any(), any(), any(Instant.class))).thenReturn(false);

        listener.onMessage(consumerRecord);

        verify(outboxEventWriter, never()).write(any());
    }

    @Test
    void malformedInventoryReservationRejectedPayloadIsNonRetryable() {
        ConsumerRecord<String, String> consumerRecord = inventoryRejectedRecord("{not-json");
        when(processedMessageStore.markProcessed(any(), any(), any(Instant.class))).thenReturn(true);

        assertThatThrownBy(() -> listener.onMessage(consumerRecord))
            .isInstanceOf(NonRetryableWorkflowMessageException.class);
    }

    @Test
    void transientProcessedMessageFailureIsRetryable() {
        ConsumerRecord<String, String> consumerRecord = inventoryRejectedRecord("""
            {
              "orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b",
              "reason": "INSUFFICIENT_STOCK"
            }
            """);
        when(processedMessageStore.markProcessed(any(), any(), any(Instant.class)))
            .thenThrow(new TransientDataAccessResourceException("temporary database issue"));

        assertThatThrownBy(() -> listener.onMessage(consumerRecord))
            .isInstanceOf(RetryableWorkflowMessageException.class);
    }

    private ConsumerRecord<String, String> inventoryRejectedRecord(String payload) {
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("order-events", 0, 0L, "key", payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.INVENTORY_RESERVATION_REJECTED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", "8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b".getBytes(StandardCharsets.UTF_8));
        headers.add("workflowId", "wf-900".getBytes(StandardCharsets.UTF_8));
        headers.add("correlationId", "corr-900".getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> consumerRecord.headers().add(header));
        return consumerRecord;
    }
}
