package com.management.eventdrivenordermanagementsystem.outbox.application.port;

import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {

    List<OutboxEventRecord> findPendingForPublish(int limit, Instant now, Instant staleClaimBefore);

    boolean claimForPublish(UUID eventId, Instant now, Instant staleClaimBefore, Instant claimedAt, String claimedBy);

    boolean markPublished(UUID eventId, Instant publishedAt);

    boolean markFailedForRetry(UUID eventId, int retryCount, Instant nextRetryAt, String lastError, Instant lastFailedAt);

    boolean markDeadLettered(UUID eventId, int retryCount, String lastError, Instant deadLetteredAt);

    boolean requestReplay(UUID eventId, Instant replayedAt, String replayedBy);

    long countPending(Instant now);
}

