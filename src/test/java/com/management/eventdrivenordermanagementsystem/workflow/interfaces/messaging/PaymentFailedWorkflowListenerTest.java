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

class PaymentFailedWorkflowListenerTest {

    private OutboxEventWriter outboxEventWriter;
    private PaymentFailedWorkflowListener listener;

    @BeforeEach
    void setUp() {
        outboxEventWriter = mock(OutboxEventWriter.class);
        listener = new PaymentFailedWorkflowListener(new ObjectMapper(), outboxEventWriter, new SimpleMeterRegistry());
    }

    @Test
    void paymentFailedEmitsInventoryReleaseRequestedEvent() {
        String payload = """
            {
              "orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b",
              "reason": "PAYMENT_PROVIDER_TIMEOUT"
            }
            """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "key", payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.PAYMENT_FAILED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", "8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b".getBytes(StandardCharsets.UTF_8));
        headers.add("workflowId", "wf-700".getBytes(StandardCharsets.UTF_8));
        headers.add("correlationId", "corr-700".getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        listener.onMessage(record);

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(captor.capture());

        EventEnvelope emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo(EventType.INVENTORY_RELEASE_REQUESTED);
        assertThat(emitted.aggregateId()).isEqualTo("8f3eec6f-c5d0-4d4b-9c44-a718ee05553b");
        assertThat(emitted.workflowId()).isEqualTo("wf-700");
        assertThat(emitted.correlationId()).isEqualTo("corr-700");
        assertThat(emitted.causationId()).isEqualTo("8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b");
    }
}

