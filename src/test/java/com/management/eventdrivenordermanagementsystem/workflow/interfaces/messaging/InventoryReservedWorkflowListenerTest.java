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

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReservedWorkflowListenerTest {

    private OutboxEventWriter outboxEventWriter;
    private JdbcProcessedMessageStore processedMessageStore;
    private InventoryReservedWorkflowListener listener;

    @BeforeEach
    void setUp() {
        outboxEventWriter = mock(OutboxEventWriter.class);
        processedMessageStore = mock(JdbcProcessedMessageStore.class);
        listener = new InventoryReservedWorkflowListener(
            new ObjectMapper(),
            outboxEventWriter,
            new WorkflowConsumerFailureClassifier(),
            processedMessageStore,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void inventoryReservedEmitsPaymentAuthorizationRequestedEvent() {
        String payload = """
            {
              "orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b",
              "currency": "USD",
              "totalAmount": 42.50,
              "reservationId": "res-200"
            }
            """;

        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("order-events", 0, 0L, "key", payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.INVENTORY_RESERVED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", "8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b".getBytes(StandardCharsets.UTF_8));
        headers.add("workflowId", "wf-200".getBytes(StandardCharsets.UTF_8));
        headers.add("correlationId", "corr-200".getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> consumerRecord.headers().add(header));

        when(processedMessageStore.markProcessed(any(), any(), any(Instant.class))).thenReturn(true);

        listener.onMessage(consumerRecord);

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(captor.capture());

        EventEnvelope emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo(EventType.PAYMENT_AUTHORIZATION_REQUESTED);
        assertThat(emitted.aggregateId()).isEqualTo("8f3eec6f-c5d0-4d4b-9c44-a718ee05553b");
        assertThat(emitted.workflowId()).isEqualTo("wf-200");
        assertThat(emitted.correlationId()).isEqualTo("corr-200");
        assertThat(emitted.payload().path("amount").asDouble()).isEqualTo(42.50d);
        assertThat(emitted.payload().path("currency").asText()).isEqualTo("USD");
    }
}
