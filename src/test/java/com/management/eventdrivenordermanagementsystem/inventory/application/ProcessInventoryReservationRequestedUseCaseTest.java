package com.management.eventdrivenordermanagementsystem.inventory.application;

import com.management.eventdrivenordermanagementsystem.inventory.application.dto.InventoryReservationRequestedCommand;
import com.management.eventdrivenordermanagementsystem.inventory.application.port.InventoryRepository;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryItem;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservation;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservationStatus;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcessInventoryReservationRequestedUseCaseTest {

    private InventoryRepository inventoryRepository;
    private OutboxEventWriter outboxEventWriter;
    private ProcessInventoryReservationRequestedUseCase useCase;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        outboxEventWriter = mock(OutboxEventWriter.class);
        useCase = new ProcessInventoryReservationRequestedUseCase(inventoryRepository, outboxEventWriter, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void reservesInventoryWhenStockIsSufficient() {
        UUID messageId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem stockItem = InventoryItem.create("SKU-1", 10, 0, Instant.parse("2026-04-01T10:00:00Z"));

        when(inventoryRepository.markMessageProcessed(any(), any(), any())).thenReturn(true);
        when(inventoryRepository.findItemBySku("SKU-1")).thenReturn(Optional.of(stockItem));
        when(inventoryRepository.saveItem(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.saveReservation(any(InventoryReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(
            new InventoryReservationRequestedCommand(
                messageId,
                orderId,
                "wf-100",
                "corr-100",
                "cause-100",
                Instant.parse("2026-04-01T10:00:05Z"),
                List.of(new InventoryReservationRequestedCommand.RequestedItem("SKU-1", 3))
            )
        );

        ArgumentCaptor<InventoryItem> stockCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryRepository).saveItem(stockCaptor.capture());
        assertThat(stockCaptor.getValue().availableQuantity()).isEqualTo(7);
        assertThat(stockCaptor.getValue().reservedQuantity()).isEqualTo(3);

        ArgumentCaptor<InventoryReservation> reservationCaptor = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(inventoryRepository).saveReservation(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().status()).isEqualTo(InventoryReservationStatus.RESERVED);

        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().eventType()).isEqualTo(EventType.INVENTORY_RESERVED);
        assertThat(envelopeCaptor.getValue().aggregateId()).isEqualTo(orderId.toString());
        assertThat(envelopeCaptor.getValue().payload().path("inventoryStatus").asText()).isEqualTo("RESERVED");
        assertThat(envelopeCaptor.getValue().payload().path("items")).hasSize(1);
        assertThat(envelopeCaptor.getValue().payload().path("items").get(0).path("status").asText()).isEqualTo("RESERVED");
    }

    @Test
    void rejectsInventoryWhenStockIsInsufficient() {
        UUID messageId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem stockItem = InventoryItem.create("SKU-1", 2, 0, Instant.parse("2026-04-01T10:00:00Z"));

        when(inventoryRepository.markMessageProcessed(any(), any(), any())).thenReturn(true);
        when(inventoryRepository.findItemBySku("SKU-1")).thenReturn(Optional.of(stockItem));
        when(inventoryRepository.saveReservation(any(InventoryReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(
            new InventoryReservationRequestedCommand(
                messageId,
                orderId,
                "wf-101",
                "corr-101",
                "cause-101",
                Instant.parse("2026-04-01T10:00:05Z"),
                List.of(new InventoryReservationRequestedCommand.RequestedItem("SKU-1", 3))
            )
        );

        verify(inventoryRepository, never()).saveItem(any());
        ArgumentCaptor<InventoryReservation> reservationCaptor = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(inventoryRepository).saveReservation(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().status()).isEqualTo(InventoryReservationStatus.REJECTED);

        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().eventType()).isEqualTo(EventType.INVENTORY_RESERVATION_REJECTED);
        assertThat(envelopeCaptor.getValue().payload().path("inventoryStatus").asText()).isEqualTo("REJECTED");
        assertThat(envelopeCaptor.getValue().payload().path("reason").asText()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void ignoresDuplicateMessagesIdempotently() {
        UUID messageId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(inventoryRepository.markMessageProcessed(any(), any(), any())).thenReturn(false);

        useCase.execute(
            new InventoryReservationRequestedCommand(
                messageId,
                orderId,
                "wf-102",
                "corr-102",
                "cause-102",
                Instant.parse("2026-04-01T10:00:05Z"),
                List.of(new InventoryReservationRequestedCommand.RequestedItem("SKU-1", 1))
            )
        );

        verify(inventoryRepository).markMessageProcessed(any(), any(), any());
        verify(inventoryRepository, never()).findItemBySku(any());
        verify(inventoryRepository, never()).saveItem(any());
        verify(inventoryRepository, never()).saveReservation(any());
        verifyNoInteractions(outboxEventWriter);
    }
}

