package com.management.eventdrivenordermanagementsystem.outbox.application.port;

import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {

    List<OutboxEventRecord> findPendingForPublish(int limit, Instant now);

    void markPublished(UUID eventId, Instant publishedAt);

    void markFailedForRetry(UUID eventId, int retryCount, Instant nextRetryAt);

    long countPending(Instant now);
}

