package com.management.eventdrivenordermanagementsystem.inventory.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReservationRequestedCommand(
    UUID messageId,
    UUID orderId,
    String workflowId,
    String correlationId,
    String causationId,
    Instant occurredAt,
    BigDecimal totalAmount,
    String currency,
    List<RequestedItem> items
) {

    public InventoryReservationRequestedCommand {
        if (messageId == null) {
            throw new IllegalArgumentException("Inventory reservation messageId must be present");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("Inventory reservation orderId must be present");
        }
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("Inventory reservation workflowId must be present");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Inventory reservation correlationId must be present");
        }
        if (causationId == null || causationId.isBlank()) {
            throw new IllegalArgumentException("Inventory reservation causationId must be present");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Inventory reservation occurredAt must be present");
        }
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("Inventory reservation totalAmount must be > 0");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Inventory reservation currency must be present");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record RequestedItem(
        String sku,
        int quantity
    ) {
        public RequestedItem {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("Inventory reservation sku must be present");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Inventory reservation quantity must be > 0");
            }
        }
    }
}

