package com.management.eventdrivenordermanagementsystem.inventory.application.port;

import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryItem;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {

    Optional<InventoryItem> findItemBySku(String sku);

    InventoryItem saveItem(InventoryItem item);

    InventoryReservation saveReservation(InventoryReservation reservation);

    boolean markMessageProcessed(String consumerName, UUID messageId, Instant processedAt);
}

