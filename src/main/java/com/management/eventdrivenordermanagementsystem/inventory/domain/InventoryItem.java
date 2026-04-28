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

    public InventoryItem reserve(int quantity, Instant now) {
        if (quantity <= 0) {
            throw new InventoryDomainException("Inventory reservation quantity must be > 0");
        }
        if (now == null) {
            throw new InventoryDomainException("Inventory updatedAt must be present");
        }
        if (availableQuantity < quantity) {
            throw new InventoryDomainException("Insufficient inventory available for sku " + sku);
        }

        return new InventoryItem(sku, availableQuantity - quantity, reservedQuantity + quantity, now);
    }

    public InventoryItem release(int quantity, Instant now) {
        if (quantity <= 0) {
            throw new InventoryDomainException("Inventory release quantity must be > 0");
        }
        if (now == null) {
            throw new InventoryDomainException("Inventory updatedAt must be present");
        }
        if (reservedQuantity < quantity) {
            throw new InventoryDomainException("Reserved inventory is insufficient for sku " + sku);
        }

        return new InventoryItem(sku, availableQuantity + quantity, reservedQuantity - quantity, now);
    }
}

