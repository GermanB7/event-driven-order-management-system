package com.management.eventdrivenordermanagementsystem.orders.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
    UUID customerId,
    String currency,
    List<CreateOrderItemCommand> items
) {
    public record CreateOrderItemCommand(
        String sku,
        int quantity,
        BigDecimal unitPrice
    ) {
    }
}

