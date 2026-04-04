package com.management.eventdrivenordermanagementsystem.orders.application;

import com.management.eventdrivenordermanagementsystem.orders.application.port.OrderRepository;
import com.management.eventdrivenordermanagementsystem.orders.domain.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MarkOrderPaymentPendingUseCase {

    private final OrderRepository orderRepository;

    public MarkOrderPaymentPendingUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void execute(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        Order updatedOrder = order.markPaymentPending(Instant.now());
        orderRepository.save(updatedOrder);
    }
}

