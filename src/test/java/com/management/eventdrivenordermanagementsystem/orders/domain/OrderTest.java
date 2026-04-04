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

    @Test
    void canMoveToInventoryReservationPendingFromCreated() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = OrderItem.create(UUID.randomUUID(), orderId, "SKU-1", 1, new BigDecimal("15.00"));

        Order order = Order.create(
            orderId,
            UUID.randomUUID(),
            "USD",
            List.of(item),
            Instant.parse("2026-04-01T10:00:00Z")
        );

        Order updated = order.markInventoryReservationPending(Instant.parse("2026-04-01T10:05:00Z"));

        assertThat(updated.status()).isEqualTo(OrderStatus.INVENTORY_RESERVATION_PENDING);
        assertThat(updated.updatedAt()).isEqualTo(Instant.parse("2026-04-01T10:05:00Z"));
    }

    @Test
    void cannotMoveToInventoryReservationPendingWhenStatusIsNotCreated() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = OrderItem.create(UUID.randomUUID(), orderId, "SKU-1", 1, new BigDecimal("15.00"));

        Order order = Order.rehydrate(
            orderId,
            UUID.randomUUID(),
            OrderStatus.CANCELLED,
            "USD",
            new BigDecimal("15.00"),
            Instant.parse("2026-04-01T10:00:00Z"),
            Instant.parse("2026-04-01T10:01:00Z"),
            List.of(item)
        );

        assertThatThrownBy(() -> order.markInventoryReservationPending(Instant.parse("2026-04-01T10:02:00Z")))
            .isInstanceOf(OrderDomainException.class)
            .hasMessageContaining("only from CREATED");
    }
}
