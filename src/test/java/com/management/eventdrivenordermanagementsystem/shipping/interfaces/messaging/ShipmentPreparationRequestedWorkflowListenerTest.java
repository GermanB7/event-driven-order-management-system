package com.management.eventdrivenordermanagementsystem.shipping.interfaces.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.shipping.application.port.ShipmentRepository;
import com.management.eventdrivenordermanagementsystem.shipping.domain.Shipment;
import com.management.eventdrivenordermanagementsystem.shipping.domain.ShipmentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipmentPreparationRequestedWorkflowListenerTest {

    private ShipmentRepository shipmentRepository;
    private OutboxEventWriter outboxEventWriter;
    private ShipmentPreparationRequestedWorkflowListener listener;

    @BeforeEach
    void setUp() {
        shipmentRepository = mock(ShipmentRepository.class);
        outboxEventWriter = mock(OutboxEventWriter.class);
        listener = new ShipmentPreparationRequestedWorkflowListener(
            new ObjectMapper(),
            shipmentRepository,
            outboxEventWriter,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void shipmentPreparationRequestedCreatesShipmentAndEmitsStartedEvent() {
        UUID orderId = UUID.randomUUID();
        String payload = """
            {
              "orderId": "%s",
              "workflowId": "wf-400",
              "correlationId": "corr-400",
              "shippingRequestId": "ship-400",
              "customerId": "47d7b31a-7b0d-4a8e-b55d-b8fef0c68d1f",
              "amount": 42.50,
              "currency": "USD"
            }
            """.formatted(orderId);

        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("order-events", 0, 0L, "key", payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.SHIPMENT_PREPARATION_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", "8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b".getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> consumerRecord.headers().add(header));

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onMessage(consumerRecord);

        org.mockito.ArgumentCaptor<Shipment> shipmentCaptor = org.mockito.ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(shipmentCaptor.capture());
        assertThat(shipmentCaptor.getValue().status()).isEqualTo(ShipmentStatus.PREPARING);

        org.mockito.ArgumentCaptor<EventEnvelope> envelopeCaptor = org.mockito.ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().eventType()).isEqualTo(EventType.SHIPMENT_PREPARATION_STARTED);
        assertThat(envelopeCaptor.getValue().payload().path("shipmentStatus").asText()).isEqualTo("PREPARING");
        assertThat(envelopeCaptor.getValue().payload().path("shippingRequestId").asText()).isEqualTo("ship-400");
    }

    @Test
    void duplicateShipmentPreparationRequestIsIgnored() {
        UUID orderId = UUID.randomUUID();
        Shipment existingShipment = new Shipment(
            UUID.randomUUID(),
            orderId,
            ShipmentStatus.PREPARING,
            Instant.parse("2026-04-01T10:00:00Z"),
            Instant.parse("2026-04-01T10:01:00Z")
        );

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existingShipment));

        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("order-events", 0, 0L, "key", """
            {"orderId": "%s", "workflowId": "wf-401", "correlationId": "corr-401"}
            """.formatted(orderId));
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.SHIPMENT_PREPARATION_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", "8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b".getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> consumerRecord.headers().add(header));

        listener.onMessage(consumerRecord);

        verify(shipmentRepository, never()).save(any());
        verify(outboxEventWriter, never()).write(any());
    }
}


