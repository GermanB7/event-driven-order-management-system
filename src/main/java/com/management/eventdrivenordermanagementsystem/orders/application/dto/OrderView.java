package com.management.eventdrivenordermanagementsystem.orders.application.dto;

import com.management.eventdrivenordermanagementsystem.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(
    UUID id,
    UUID customerId,
    OrderStatus status,
    String currency,
    BigDecimal totalAmount,
    Instant createdAt,
    Instant updatedAt,
    List<OrderItemView> items
) {
    public record OrderItemView(
        UUID id,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
    ) {
    }
}

