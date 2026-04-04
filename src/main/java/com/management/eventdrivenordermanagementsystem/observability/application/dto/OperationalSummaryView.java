package com.management.eventdrivenordermanagementsystem.observability.application.dto;

public record OperationalSummaryView(
    long totalOrdersCreated,
    long totalOrdersConfirmed,
    long totalOrdersCancelled,
    long totalOrdersFulfillmentRequested,
    long pendingOutboxEventCount,
    long failedOutboxEventCount,
    long deadLetteredEventCount,
    long totalEventsPublished
) {
}

