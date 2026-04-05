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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreatedWorkflowListenerTest {

    private OutboxEventWriter outboxEventWriter;
    private JdbcProcessedMessageStore processedMessageStore;
    private OrderCreatedWorkflowListener listener;

    @BeforeEach
    void setUp() {
        outboxEventWriter = mock(OutboxEventWriter.class);
        processedMessageStore = mock(JdbcProcessedMessageStore.class);
        listener = new OrderCreatedWorkflowListener(
            new ObjectMapper(),
            outboxEventWriter,
            new WorkflowConsumerFailureClassifier(),
            processedMessageStore,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void orderCreatedEmitsInventoryReservationRequestedEvent() {
        String payload = """
            {
              "orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b",
              "items": [
                {"sku": "SKU-1", "quantity": 2}
              ]
            }
            """;

        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("order-events", 0, 0L, "key", payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.ORDER_CREATED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        headers.add("workflowId", "wf-100".getBytes(StandardCharsets.UTF_8));
        headers.add("correlationId", "corr-100".getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> consumerRecord.headers().add(header));

        when(processedMessageStore.markProcessed(any(), any(), any(Instant.class))).thenReturn(true);

        listener.onMessage(consumerRecord);

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(captor.capture());

        EventEnvelope emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo(EventType.INVENTORY_RESERVATION_REQUESTED);
        assertThat(emitted.aggregateId()).isEqualTo("8f3eec6f-c5d0-4d4b-9c44-a718ee05553b");
        assertThat(emitted.workflowId()).isEqualTo("wf-100");
        assertThat(emitted.correlationId()).isEqualTo("corr-100");
        assertThat(emitted.payload().path("items").size()).isEqualTo(1);
    }
}
