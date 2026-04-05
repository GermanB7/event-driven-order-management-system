package com.management.eventdrivenordermanagementsystem.observability.application.dto;

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
    int replayCount,
    String replayedBy,
    String replayedAt,
    String occurredAt,
    String nextRetryAt
) {
}

