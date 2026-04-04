package com.management.eventdrivenordermanagementsystem.orders.application;

import com.management.eventdrivenordermanagementsystem.orders.application.port.OrderRepository;
import com.management.eventdrivenordermanagementsystem.orders.domain.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MarkOrderCancelledUseCase {

    private final OrderRepository orderRepository;

    public MarkOrderCancelledUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void execute(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        Order updatedOrder = order.markCancelled(Instant.now());
        orderRepository.save(updatedOrder);
    }
}

