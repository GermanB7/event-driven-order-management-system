package com.management.eventdrivenordermanagementsystem.orders.infrastructure.persistence;

import com.management.eventdrivenordermanagementsystem.orders.domain.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items", schema = "orders")
public class OrderItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    protected OrderItemJpaEntity() {
    }

    public static OrderItemJpaEntity fromDomain(OrderItem item, OrderJpaEntity order) {
        OrderItemJpaEntity entity = new OrderItemJpaEntity();
        entity.id = item.id();
        entity.order = order;
        entity.sku = item.sku();
        entity.quantity = item.quantity();
        entity.unitPrice = item.unitPrice();
        entity.lineTotal = item.lineTotal();
        return entity;
    }

    public OrderItem toDomain() {
        return OrderItem.rehydrate(
            id,
            order.getId(),
            sku,
            quantity,
            unitPrice,
            lineTotal
        );
    }
}
