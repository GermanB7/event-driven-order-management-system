package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.inventory.application.port.InventoryRepository;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryItem;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservation;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservationStatus;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.TransientDataAccessResourceException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReleaseRequestedListenerTest {

    private InventoryRepository inventoryRepository;
    private OutboxEventWriter outboxEventWriter;
    private InventoryReleaseRequestedListener listener;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        outboxEventWriter = mock(OutboxEventWriter.class);
        listener = new InventoryReleaseRequestedListener(
            new ObjectMapper(),
            inventoryRepository,
            outboxEventWriter,
            new InventoryConsumerFailureClassifier(),
            new SimpleMeterRegistry()
        );
    }

    @Test
    void inventoryReleaseRequestedEmitsInventoryReleasedEvent() {
        UUID orderId = UUID.fromString("8f3eec6f-c5d0-4d4b-9c44-a718ee05553b");
        UUID reservationId = UUID.randomUUID();
        InventoryReservation reservation = InventoryReservation.reserved(
            reservationId,
            orderId,
            "SKU-1",
            2,
            Instant.parse("2026-04-01T10:00:00Z")
        );
        InventoryItem stockItem = InventoryItem.create("SKU-1", 8, 2, Instant.parse("2026-04-01T10:00:00Z"));
        String payload = """
            {
              "orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b",
              "workflowId": "wf-701",
              "correlationId": "corr-701",
              "reason": "PAYMENT_FAILED"
            }
            """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "key", payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.INVENTORY_RELEASE_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", "8e4725ea-c3a6-4f6b-b22f-7b8f8ac3b39b".getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        when(inventoryRepository.markMessageProcessed(any(), any(), any(Instant.class))).thenReturn(true);
        when(inventoryRepository.findReservationsByOrderId(orderId)).thenReturn(List.of(reservation));
        when(inventoryRepository.findItemBySku("SKU-1")).thenReturn(Optional.of(stockItem));
        when(inventoryRepository.saveItem(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.updateReservationStatus(any(), any(), any())).thenAnswer(invocation -> reservation);

        listener.onMessage(record);

        verify(inventoryRepository).markMessageProcessed(any(), any(), any(Instant.class));
        ArgumentCaptor<InventoryItem> stockCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryRepository).saveItem(stockCaptor.capture());
        assertThat(stockCaptor.getValue().availableQuantity()).isEqualTo(10);
        assertThat(stockCaptor.getValue().reservedQuantity()).isZero();
        verify(inventoryRepository).updateReservationStatus(reservationId, InventoryReservationStatus.RELEASED, stockCaptor.getValue().updatedAt());

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(captor.capture());

        EventEnvelope emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo(EventType.INVENTORY_RELEASED);
        assertThat(emitted.aggregateId()).isEqualTo("8f3eec6f-c5d0-4d4b-9c44-a718ee05553b");
        assertThat(emitted.workflowId()).isEqualTo("wf-701");
        assertThat(emitted.correlationId()).isEqualTo("corr-701");
        assertThat(emitted.payload().path("inventoryStatus").asText()).isEqualTo("RELEASED");
    }

    @Test
    void duplicateMessageIsIgnored() {
        ConsumerRecord<String, String> record = validRecord();
        when(inventoryRepository.markMessageProcessed(any(), any(), any(Instant.class))).thenReturn(false);

        listener.onMessage(record);

        verify(outboxEventWriter, never()).write(any());
    }

    @Test
    void malformedPayloadIsClassifiedAsNonRetryable() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "key", "{invalid-json");
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.INVENTORY_RELEASE_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        assertThatThrownBy(() -> listener.onMessage(record))
            .isInstanceOf(NonRetryableInventoryMessageException.class);

        verify(outboxEventWriter, never()).write(any());
    }

    @Test
    void transientRepositoryFailureIsClassifiedAsRetryable() {
        ConsumerRecord<String, String> record = validRecord();
        when(inventoryRepository.markMessageProcessed(any(), any(), any(Instant.class)))
            .thenThrow(new TransientDataAccessResourceException("database unavailable"));

        assertThatThrownBy(() -> listener.onMessage(record))
            .isInstanceOf(RetryableInventoryMessageException.class);

        verify(outboxEventWriter, never()).write(any());
    }

    private ConsumerRecord<String, String> validRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "order-events",
            0,
            0L,
            "key",
            """
                {"orderId": "8f3eec6f-c5d0-4d4b-9c44-a718ee05553b", "workflowId": "wf-701", "correlationId": "corr-701"}
                """
        );
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.INVENTORY_RELEASE_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));
        return record;
    }
}
