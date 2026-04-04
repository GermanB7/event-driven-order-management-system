package com.management.eventdrivenordermanagementsystem.inventory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.management.eventdrivenordermanagementsystem.inventory.application.dto.InventoryReservationRequestedCommand;
import com.management.eventdrivenordermanagementsystem.inventory.application.port.InventoryRepository;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryItem;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservation;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessInventoryReservationRequestedUseCase {

    private static final String AGGREGATE_TYPE_INVENTORY = "INVENTORY";
    private static final String CONSUMER_NAME = "inventory-reservation-requested-listener";

    private final InventoryRepository inventoryRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;

    public ProcessInventoryReservationRequestedUseCase(
        InventoryRepository inventoryRepository,
        OutboxEventWriter outboxEventWriter,
        ObjectMapper objectMapper
    ) {
        this.inventoryRepository = inventoryRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void execute(InventoryReservationRequestedCommand command) {
        Instant now = Instant.now();
        if (!inventoryRepository.markMessageProcessed(CONSUMER_NAME, command.messageId(), now)) {
            return;
        }

        List<InventoryReservationRequestedCommand.RequestedItem> requestedItems = command.items();
        if (requestedItems.isEmpty()) {
            publishRejected(command, now, "EMPTY_RESERVATION_REQUEST", null, requestedItems);
            return;
        }

        List<InventoryItem> stockItems = new ArrayList<>(requestedItems.size());
        String failureReason = null;
        String failedSku = null;

        for (int index = 0; index < requestedItems.size() && failureReason == null; index++) {
            InventoryReservationRequestedCommand.RequestedItem requestedItem = requestedItems.get(index);
            InventoryItem stockItem = inventoryRepository.findItemBySku(requestedItem.sku()).orElse(null);
            if (stockItem == null) {
                failureReason = "SKU_NOT_FOUND";
                failedSku = requestedItem.sku();
            } else if (stockItem.availableQuantity() < requestedItem.quantity()) {
                failureReason = "INSUFFICIENT_STOCK";
                failedSku = requestedItem.sku();
            } else {
                stockItems.add(stockItem);
            }
        }

        if (failureReason != null) {
            persistRejectedReservations(command, now, requestedItems);
            publishRejected(command, now, failureReason, failedSku, requestedItems);
            return;
        }

        List<InventoryReservation> reservations = new ArrayList<>(requestedItems.size());
        for (int index = 0; index < requestedItems.size(); index++) {
            InventoryReservationRequestedCommand.RequestedItem requestedItem = requestedItems.get(index);
            InventoryItem reservedStockItem = stockItems.get(index).reserve(requestedItem.quantity(), now);
            inventoryRepository.saveItem(reservedStockItem);

            InventoryReservation reservation = InventoryReservation.reserved(
                UUID.randomUUID(),
                command.orderId(),
                requestedItem.sku(),
                requestedItem.quantity(),
                now
            );
            inventoryRepository.saveReservation(reservation);
            reservations.add(reservation);
        }

        publishReserved(command, now, reservations, requestedItems);
    }

    private void persistRejectedReservations(
        InventoryReservationRequestedCommand command,
        Instant now,
        List<InventoryReservationRequestedCommand.RequestedItem> requestedItems
    ) {
        for (InventoryReservationRequestedCommand.RequestedItem requestedItem : requestedItems) {
            inventoryRepository.saveReservation(
                InventoryReservation.rejected(
                    UUID.randomUUID(),
                    command.orderId(),
                    requestedItem.sku(),
                    requestedItem.quantity(),
                    now
                )
            );
        }
    }

    private void publishReserved(
        InventoryReservationRequestedCommand command,
        Instant now,
        List<InventoryReservation> reservations,
        List<InventoryReservationRequestedCommand.RequestedItem> requestedItems
    ) {
        outboxEventWriter.write(
            new EventEnvelope(
                UUID.randomUUID(),
                EventType.INVENTORY_RESERVED,
                command.orderId().toString(),
                AGGREGATE_TYPE_INVENTORY,
                command.workflowId(),
                command.correlationId(),
                command.causationId(),
                now,
                1,
                buildReservedPayload(command, reservations, requestedItems)
            )
        );
    }

    private void publishRejected(
        InventoryReservationRequestedCommand command,
        Instant now,
        String failureReason,
        String failedSku,
        List<InventoryReservationRequestedCommand.RequestedItem> requestedItems
    ) {
        outboxEventWriter.write(
            new EventEnvelope(
                UUID.randomUUID(),
                EventType.INVENTORY_RESERVATION_REJECTED,
                command.orderId().toString(),
                AGGREGATE_TYPE_INVENTORY,
                command.workflowId(),
                command.correlationId(),
                command.causationId(),
                now,
                1,
                buildRejectedPayload(command, failureReason, failedSku, requestedItems)
            )
        );
    }

    private ObjectNode buildReservedPayload(
        InventoryReservationRequestedCommand command,
        List<InventoryReservation> reservations,
        List<InventoryReservationRequestedCommand.RequestedItem> requestedItems
    ) {
        ObjectNode payload = basePayload(command, "RESERVED");
        ArrayNode itemsNode = payload.putArray("items");
        for (int index = 0; index < requestedItems.size(); index++) {
            InventoryReservationRequestedCommand.RequestedItem requestedItem = requestedItems.get(index);
            InventoryReservation reservation = reservations.get(index);
            ObjectNode itemNode = itemsNode.addObject();
            itemNode.put("sku", requestedItem.sku());
            itemNode.put("quantity", requestedItem.quantity());
            itemNode.put("reservationId", reservation.id().toString());
            itemNode.put("status", reservation.status().name());
        }
        return payload;
    }

    private ObjectNode buildRejectedPayload(
        InventoryReservationRequestedCommand command,
        String failureReason,
        String failedSku,
        List<InventoryReservationRequestedCommand.RequestedItem> requestedItems
    ) {
        ObjectNode payload = basePayload(command, "REJECTED");
        payload.put("reason", failureReason);
        if (failedSku != null) {
            payload.put("failedSku", failedSku);
        }

        ArrayNode itemsNode = payload.putArray("items");
        for (InventoryReservationRequestedCommand.RequestedItem requestedItem : requestedItems) {
            ObjectNode itemNode = itemsNode.addObject();
            itemNode.put("sku", requestedItem.sku());
            itemNode.put("quantity", requestedItem.quantity());
        }
        return payload;
    }

    private ObjectNode basePayload(InventoryReservationRequestedCommand command, String inventoryStatus) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", command.orderId().toString());
        payload.put("workflowId", command.workflowId());
        payload.put("correlationId", command.correlationId());
        payload.put("causationId", command.causationId());
        payload.put("inventoryStatus", inventoryStatus);
        return payload;
    }
}



