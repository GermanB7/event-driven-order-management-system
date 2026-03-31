package com.management.eventdrivenordermanagementsystem.orders.application.dto;

import com.management.eventdrivenordermanagementsystem.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateOrderResult(
    UUID orderId,
    OrderStatus status,
    String currency,
    BigDecimal totalAmount,
    Instant createdAt
) {
}

