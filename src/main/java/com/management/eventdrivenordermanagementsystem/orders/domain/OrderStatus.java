package com.management.eventdrivenordermanagementsystem.orders.domain;

public enum OrderStatus {
    CREATED,
    INVENTORY_RESERVATION_PENDING,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    FULFILLMENT_REQUESTED,
    CANCELLED
}

