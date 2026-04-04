package com.management.eventdrivenordermanagementsystem.observability.application.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkflowTimelineEventView(
    UUID eventId,
    String eventType,
    String workflowId,
    String correlationId,
    String causationId,
    String publishStatus,
    int retryCount,
    Instant occurredAt,
    Instant publishedAt,
    Instant nextRetryAt
) {
}

