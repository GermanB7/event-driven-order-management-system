package com.management.eventdrivenordermanagementsystem.orders.infrastructure.persistence;

import com.management.eventdrivenordermanagementsystem.orders.application.port.OrderRepository;
import com.management.eventdrivenordermanagementsystem.orders.domain.Order;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class OrderRepositoryJpaAdapter implements OrderRepository {

    private final SpringDataOrderJpaRepository springDataOrderJpaRepository;

    public OrderRepositoryJpaAdapter(SpringDataOrderJpaRepository springDataOrderJpaRepository) {
        this.springDataOrderJpaRepository = springDataOrderJpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity saved = springDataOrderJpaRepository.save(OrderJpaEntity.fromDomain(order));
        return saved.toDomain();
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return springDataOrderJpaRepository.findById(orderId)
            .map(OrderJpaEntity::toDomain);
    }
}

