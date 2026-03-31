package com.management.eventdrivenordermanagementsystem.orders.infrastructure.persistence;

import com.management.eventdrivenordermanagementsystem.orders.domain.Order;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderItem;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", schema = "orders")
public class OrderJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    public static OrderJpaEntity fromDomain(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.id = order.id();
        entity.customerId = order.customerId();
        entity.status = order.status().name();
        entity.currency = order.currency();
        entity.totalAmount = order.totalAmount();
        entity.createdAt = order.createdAt();
        entity.updatedAt = order.updatedAt();

        for (OrderItem item : order.items()) {
            entity.items.add(OrderItemJpaEntity.fromDomain(item, entity));
        }
        return entity;
    }

    public Order toDomain() {
        List<OrderItem> domainItems = items.stream()
            .map(OrderItemJpaEntity::toDomain)
            .toList();

        return Order.rehydrate(
            id,
            customerId,
            OrderStatus.valueOf(status),
            currency,
            totalAmount,
            createdAt,
            updatedAt,
            domainItems
        );
    }

    public UUID getId() {
        return id;
    }
}
