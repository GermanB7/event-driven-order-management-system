package com.management.eventdrivenordermanagementsystem.inventory.domain;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservation(
    UUID id,
    UUID orderId,
    String sku,
    int quantity,
    InventoryReservationStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public static InventoryReservation create(UUID id, UUID orderId, String sku, int quantity, Instant now) {
        if (id == null) {
            throw new InventoryDomainException("InventoryReservation id must be present");
        }
        if (orderId == null) {
            throw new InventoryDomainException("InventoryReservation orderId must be present");
        }
        if (sku == null || sku.isBlank()) {
            throw new InventoryDomainException("InventoryReservation sku must be present");
        }
        if (quantity <= 0) {
            throw new InventoryDomainException("InventoryReservation quantity must be > 0");
        }
        if (now == null) {
            throw new InventoryDomainException("InventoryReservation createdAt must be present");
        }

        return new InventoryReservation(id, orderId, sku, quantity, InventoryReservationStatus.PENDING, now, now);
    }

    public static InventoryReservation reserved(UUID id, UUID orderId, String sku, int quantity, Instant now) {
        return withStatus(id, orderId, sku, quantity, InventoryReservationStatus.RESERVED, now);
    }

    public static InventoryReservation rejected(UUID id, UUID orderId, String sku, int quantity, Instant now) {
        return withStatus(id, orderId, sku, quantity, InventoryReservationStatus.REJECTED, now);
    }

    private static InventoryReservation withStatus(
        UUID id,
        UUID orderId,
        String sku,
        int quantity,
        InventoryReservationStatus status,
        Instant now
    ) {
        if (id == null) {
            throw new InventoryDomainException("InventoryReservation id must be present");
        }
        if (orderId == null) {
            throw new InventoryDomainException("InventoryReservation orderId must be present");
        }
        if (sku == null || sku.isBlank()) {
            throw new InventoryDomainException("InventoryReservation sku must be present");
        }
        if (quantity <= 0) {
            throw new InventoryDomainException("InventoryReservation quantity must be > 0");
        }
        if (now == null) {
            throw new InventoryDomainException("InventoryReservation updatedAt must be present");
        }

        return new InventoryReservation(id, orderId, sku, quantity, status, now, now);
    }
}

