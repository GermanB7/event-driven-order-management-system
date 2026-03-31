package com.management.eventdrivenordermanagementsystem.orders.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void cannotCreateOrderWithEmptyItems() {
        assertThatThrownBy(() -> Order.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "USD",
            List.of(),
            Instant.now()
        )).isInstanceOf(OrderDomainException.class)
            .hasMessageContaining("at least one item");
    }

    @Test
    void cannotCreateOrderWithInvalidQuantity() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> OrderItem.create(
            UUID.randomUUID(),
            orderId,
            "SKU-1",
            0,
            new BigDecimal("10.00")
        )).isInstanceOf(OrderDomainException.class)
            .hasMessageContaining("quantity");
    }

    @Test
    void cannotCreateOrderWithInvalidUnitPrice() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> OrderItem.create(
            UUID.randomUUID(),
            orderId,
            "SKU-1",
            1,
            BigDecimal.ZERO
        )).isInstanceOf(OrderDomainException.class)
            .hasMessageContaining("unitPrice");
    }

    @Test
    void calculatesTotalAmountFromItems() {
        UUID orderId = UUID.randomUUID();
        OrderItem item1 = OrderItem.create(UUID.randomUUID(), orderId, "SKU-1", 2, new BigDecimal("10.00"));
        OrderItem item2 = OrderItem.create(UUID.randomUUID(), orderId, "SKU-2", 1, new BigDecimal("5.50"));

        Order order = Order.create(
            orderId,
            UUID.randomUUID(),
            "USD",
            List.of(item1, item2),
            Instant.now()
        );

        assertThat(order.totalAmount()).isEqualByComparingTo("25.50");
    }

    @Test
    void initialStatusIsCreated() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = OrderItem.create(UUID.randomUUID(), orderId, "SKU-1", 1, new BigDecimal("15.00"));

        Order order = Order.create(
            orderId,
            UUID.randomUUID(),
            "USD",
            List.of(item),
            Instant.now()
        );

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
    }
}

