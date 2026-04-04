package com.management.eventdrivenordermanagementsystem.shipping.domain;

import java.time.Instant;
import java.util.UUID;

public record Shipment(
    UUID id,
    UUID orderId,
    ShipmentStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public static Shipment createPending(UUID id, UUID orderId, Instant now) {
        validateBase(id, orderId, now);
        return new Shipment(id, orderId, ShipmentStatus.PENDING, now, now);
    }

    public Shipment markPreparing(Instant now) {
        if (status != ShipmentStatus.PENDING) {
            throw new ShipmentDomainException("Shipment can move to PREPARING only from PENDING");
        }
        if (now == null) {
            throw new ShipmentDomainException("Shipment updatedAt must be present");
        }
        return new Shipment(id, orderId, ShipmentStatus.PREPARING, createdAt, now);
    }

    private static void validateBase(UUID id, UUID orderId, Instant now) {
        if (id == null) {
            throw new ShipmentDomainException("Shipment id must be present");
        }
        if (orderId == null) {
            throw new ShipmentDomainException("Shipment orderId must be present");
        }
        if (now == null) {
            throw new ShipmentDomainException("Shipment createdAt must be present");
        }
    }
}

