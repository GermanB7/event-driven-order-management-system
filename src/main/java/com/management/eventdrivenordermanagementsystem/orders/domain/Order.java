package com.management.eventdrivenordermanagementsystem.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Order(
    UUID id,
    UUID customerId,
    OrderStatus status,
    String currency,
    BigDecimal totalAmount,
    Instant createdAt,
    Instant updatedAt,
    List<OrderItem> items
) {

    public static Order create(
        UUID id,
        UUID customerId,
        String currency,
        List<OrderItem> items,
        Instant now
    ) {
        validateBase(id, customerId, currency, items, now);

        BigDecimal totalAmount = calculateTotal(items);
        return new Order(id, customerId, OrderStatus.CREATED, currency, totalAmount, now, now, List.copyOf(items));
    }

    public static Order rehydrate(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItem> items
    ) {
        validateBase(id, customerId, currency, items, createdAt);
        if (status == null) {
            throw new OrderDomainException("Order status must be present");
        }
        if (updatedAt == null) {
            throw new OrderDomainException("Order updatedAt must be present");
        }
        if (totalAmount == null || totalAmount.signum() < 0) {
            throw new OrderDomainException("Order totalAmount must be >= 0");
        }
        if (calculateTotal(items).compareTo(totalAmount) != 0) {
            throw new OrderDomainException("Order totalAmount is inconsistent with items");
        }

        return new Order(id, customerId, status, currency, totalAmount, createdAt, updatedAt, List.copyOf(items));
    }

    private static void validateBase(UUID id, UUID customerId, String currency, List<OrderItem> items, Instant createdAt) {
        if (id == null) {
            throw new OrderDomainException("Order id must be present");
        }
        if (customerId == null) {
            throw new OrderDomainException("Order customerId must be present");
        }
        if (currency == null || currency.isBlank()) {
            throw new OrderDomainException("Order currency must be present");
        }
        if (createdAt == null) {
            throw new OrderDomainException("Order createdAt must be present");
        }
        if (items == null || items.isEmpty()) {
            throw new OrderDomainException("Order must contain at least one item");
        }

        for (OrderItem item : items) {
            if (!Objects.equals(item.orderId(), id)) {
                throw new OrderDomainException("Order item orderId must match order id");
            }
        }
    }

    private static BigDecimal calculateTotal(List<OrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.lineTotal());
        }
        return total;
    }

    public List<OrderItem> itemsCopy() {
        return new ArrayList<>(items);
    }
}

