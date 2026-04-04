package com.management.eventdrivenordermanagementsystem.observability.application.dto;

import java.util.List;

public record FailedAsyncOperationView(
    String eventId,
    String eventType,
    String orderId,
    String workflowId,
    String consumerName,
    int retryCount,
    String failureReason,
    String publishStatus,
    boolean deadLettered,
    String occurredAt,
    String nextRetryAt
) {
}

