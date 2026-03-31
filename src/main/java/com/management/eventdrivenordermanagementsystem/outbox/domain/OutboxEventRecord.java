package com.management.eventdrivenordermanagementsystem.outbox.domain;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventRecord(
    UUID id,
    String aggregateId,
    String aggregateType,
    String eventType,
    String payload,
    String headers,
    OutboxEventStatus status,
    Instant occurredAt,
    Instant publishedAt,
    int retryCount,
    Instant nextRetryAt
) {
}

