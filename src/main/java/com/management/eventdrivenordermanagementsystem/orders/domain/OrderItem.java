package com.management.eventdrivenordermanagementsystem.orders.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record OrderItem(
    UUID id,
    UUID orderId,
    String sku,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {

    public static OrderItem create(UUID id, UUID orderId, String sku, int quantity, BigDecimal unitPrice) {
        if (id == null) {
            throw new OrderDomainException("Order item id must be present");
        }
        if (orderId == null) {
            throw new OrderDomainException("Order item orderId must be present");
        }
        if (sku == null || sku.isBlank()) {
            throw new OrderDomainException("Order item sku must be present");
        }
        if (quantity <= 0) {
            throw new OrderDomainException("Order item quantity must be greater than 0");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new OrderDomainException("Order item unitPrice must be greater than 0");
        }

        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        return new OrderItem(id, orderId, sku, quantity, unitPrice, lineTotal);
    }

    public static OrderItem rehydrate(
        UUID id,
        UUID orderId,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
    ) {
        OrderItem item = create(id, orderId, sku, quantity, unitPrice);
        if (lineTotal == null || item.lineTotal.compareTo(lineTotal) != 0) {
            throw new OrderDomainException("Order item lineTotal is inconsistent");
        }
        return item;
    }

    public OrderItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        Objects.requireNonNull(lineTotal, "lineTotal must not be null");
    }
}

