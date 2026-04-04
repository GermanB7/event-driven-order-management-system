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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PaymentAuthorizedWorkflowListenerTest {

    private OutboxEventWriter outboxEventWriter;
    private PaymentAuthorizedWorkflowListener listener;

    @BeforeEach
    void setUp() {
        outboxEventWriter = mock(OutboxEventWriter.class);
        listener = new PaymentAuthorizedWorkflowListener(new ObjectMapper(), outboxEventWriter, new SimpleMeterRegistry());
    }

    @Test
    void paymentAuthorizedEmitsOrderConfirmedAndShipmentPreparationRequestedEvents() {
        String payload = """
            {
              "orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b",
              "customerId": "47d7b31a-7b0d-4a8e-b55d-b8fef0c68d1f",
              "amount": 42.50,
              "currency": "USD"
            }
            """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "key", payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.PAYMENT_AUTHORIZED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", "8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b".getBytes(StandardCharsets.UTF_8));
        headers.add("workflowId", "wf-300".getBytes(StandardCharsets.UTF_8));
        headers.add("correlationId", "corr-300".getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        listener.onMessage(record);

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter, times(2)).write(captor.capture());

        assertThat(captor.getAllValues()).hasSize(2);
        EventEnvelope orderConfirmed = captor.getAllValues().get(0);
        EventEnvelope shipmentRequested = captor.getAllValues().get(1);

        assertThat(orderConfirmed.eventType()).isEqualTo(EventType.ORDER_CONFIRMED);
        assertThat(orderConfirmed.aggregateId()).isEqualTo("8f3eec6f-c5d0-4d4b-9c44-a718ee05553b");
        assertThat(orderConfirmed.workflowId()).isEqualTo("wf-300");
        assertThat(orderConfirmed.correlationId()).isEqualTo("corr-300");
        assertThat(orderConfirmed.causationId()).isEqualTo("8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b");

        assertThat(shipmentRequested.eventType()).isEqualTo(EventType.SHIPMENT_PREPARATION_REQUESTED);
        assertThat(shipmentRequested.aggregateId()).isEqualTo("8f3eec6f-c5d0-4d4b-9c44-a718ee05553b");
        assertThat(shipmentRequested.workflowId()).isEqualTo("wf-300");
        assertThat(shipmentRequested.correlationId()).isEqualTo("corr-300");
        assertThat(shipmentRequested.payload().path("shippingRequestId").asText()).isNotBlank();
        assertThat(shipmentRequested.payload().path("shipmentStatus").asText()).isEqualTo("PENDING");
    }
}


