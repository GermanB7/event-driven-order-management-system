package com.management.eventdrivenordermanagementsystem.inventory.domain;

import java.time.Instant;

public record InventoryItem(
    String sku,
    int availableQuantity,
    int reservedQuantity,
    Instant updatedAt
) {

    public static InventoryItem create(String sku, int availableQuantity, int reservedQuantity, Instant now) {
        if (sku == null || sku.isBlank()) {
            throw new InventoryDomainException("Inventory sku must be present");
        }
        if (availableQuantity < 0) {
            throw new InventoryDomainException("Inventory availableQuantity must be >= 0");
        }
        if (reservedQuantity < 0) {
            throw new InventoryDomainException("Inventory reservedQuantity must be >= 0");
        }
        if (now == null) {
            throw new InventoryDomainException("Inventory updatedAt must be present");
        }
        return new InventoryItem(sku, availableQuantity, reservedQuantity, now);
    }
}

