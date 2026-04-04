package com.management.eventdrivenordermanagementsystem.orders.application;

import com.management.eventdrivenordermanagementsystem.orders.application.port.OrderRepository;
import com.management.eventdrivenordermanagementsystem.orders.domain.Order;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderItem;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarkOrderConfirmedUseCaseTest {

    private OrderRepository orderRepository;
    private MarkOrderConfirmedUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        useCase = new MarkOrderConfirmedUseCase(orderRepository);
    }

    @Test
    void marksPaymentPendingOrderAsConfirmed() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = OrderItem.create(UUID.randomUUID(), orderId, "SKU-1", 2, new BigDecimal("10.00"));
        Order order = Order.rehydrate(
            orderId,
            UUID.randomUUID(),
            OrderStatus.PAYMENT_PENDING,
            "USD",
            new BigDecimal("20.00"),
            Instant.parse("2026-04-01T09:00:00Z"),
            Instant.parse("2026-04-01T09:02:00Z"),
            List.of(item)
        );

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(orderId);

        org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}

