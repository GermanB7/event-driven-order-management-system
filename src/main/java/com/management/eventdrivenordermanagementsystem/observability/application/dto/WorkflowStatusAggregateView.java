package com.management.eventdrivenordermanagementsystem.observability.application.dto;

public record WorkflowStatusAggregateView(
    String orderId,
    String orderStatus,
    String inventoryReservationStatus,
    String paymentStatus,
    String shipmentStatus,
    long totalEventCount,
    long failedEventCount,
    long deadLetteredEventCount
) {
}

