package com.management.eventdrivenordermanagementsystem.orders.application;

import com.management.eventdrivenordermanagementsystem.orders.application.dto.OrderView;
import com.management.eventdrivenordermanagementsystem.orders.application.port.OrderRepository;
import com.management.eventdrivenordermanagementsystem.orders.domain.Order;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetOrderByIdUseCase {

    private final OrderRepository orderRepository;

    public GetOrderByIdUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public OrderView execute(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        List<OrderView.OrderItemView> items = order.items().stream()
            .map(this::toItemView)
            .toList();

        return new OrderView(
            order.id(),
            order.customerId(),
            order.status(),
            order.currency(),
            order.totalAmount(),
            order.createdAt(),
            order.updatedAt(),
            items
        );
    }

    private OrderView.OrderItemView toItemView(OrderItem item) {
        return new OrderView.OrderItemView(
            item.id(),
            item.sku(),
            item.quantity(),
            item.unitPrice(),
            item.lineTotal()
        );
    }
}

