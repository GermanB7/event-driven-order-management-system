package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryReleasedWorkflowListenerTest {

    private OutboxEventWriter outboxEventWriter;
    private InventoryReleasedWorkflowListener listener;

    @BeforeEach
    void setUp() {
        outboxEventWriter = mock(OutboxEventWriter.class);
        listener = new InventoryReleasedWorkflowListener(new ObjectMapper(), outboxEventWriter, new SimpleMeterRegistry());
    }

    @Test
    void inventoryReleasedEmitsOrderCancelledEvent() {
        String payload = """
            {
              "orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b",
              "workflowId": "wf-702",
              "correlationId": "corr-702",
              "reason": "PAYMENT_FAILED"
            }
            """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "key", payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.INVENTORY_RELEASED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", "8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b".getBytes(StandardCharsets.UTF_8));
        headers.add("workflowId", "wf-702".getBytes(StandardCharsets.UTF_8));
        headers.add("correlationId", "corr-702".getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        listener.onMessage(record);

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(captor.capture());

        EventEnvelope emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo(EventType.ORDER_CANCELLED);
        assertThat(emitted.aggregateId()).isEqualTo("8f3eec6f-c5d0-4d4b-9c44-a718ee05553b");
        assertThat(emitted.workflowId()).isEqualTo("wf-702");
        assertThat(emitted.correlationId()).isEqualTo("corr-702");
        assertThat(emitted.payload().path("orderStatus").asText()).isEqualTo("CANCELLED");
    }
}


