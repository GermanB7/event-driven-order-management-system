package com.management.eventdrivenordermanagementsystem.orders.application.port;

import com.management.eventdrivenordermanagementsystem.orders.domain.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(UUID orderId);
}

