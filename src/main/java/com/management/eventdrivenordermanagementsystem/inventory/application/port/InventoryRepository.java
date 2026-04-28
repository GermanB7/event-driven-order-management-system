package com.management.eventdrivenordermanagementsystem.inventory.application.port;

import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryItem;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservation;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {

    Optional<InventoryItem> findItemBySku(String sku);

    InventoryItem saveItem(InventoryItem item);

    InventoryReservation saveReservation(InventoryReservation reservation);

    List<InventoryReservation> findReservationsByOrderId(UUID orderId);

    InventoryReservation updateReservationStatus(UUID reservationId, InventoryReservationStatus status, Instant updatedAt);

    boolean markMessageProcessed(String consumerName, UUID messageId, Instant processedAt);
}

