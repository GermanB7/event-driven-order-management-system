package com.management.eventdrivenordermanagementsystem.outbox.application;

import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventPublisher;
import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventRepository;
import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxEventRepository repository;
    private final OutboxEventPublisher publisher;
    private final Clock clock;
    private final int batchSize;
    private final Duration retryDelay;
    private final Duration claimTtl;
    private final int maxRetries;
    private final String relayInstanceId;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Counter deadLetterCounter;

    @Autowired
    public OutboxRelayService(
        OutboxEventRepository repository,
        OutboxEventPublisher publisher,
        MeterRegistry meterRegistry,
        @Value("${outbox.relay.batch-size:50}") int batchSize,
        @Value("${outbox.relay.retry-delay:PT30S}") Duration retryDelay,
        @Value("${outbox.relay.claim-ttl:PT30S}") Duration claimTtl,
        @Value("${outbox.relay.max-retries:5}") int maxRetries,
        @Value("${outbox.relay.instance-id:${spring.application.name:outbox-relay}}") String relayInstanceId
    ) {
        this(repository, publisher, meterRegistry, Clock.systemUTC(), batchSize, retryDelay, claimTtl, maxRetries, relayInstanceId);
    }

    OutboxRelayService(
        OutboxEventRepository repository,
        OutboxEventPublisher publisher,
        MeterRegistry meterRegistry,
        Clock clock,
        int batchSize,
        Duration retryDelay,
        Duration claimTtl,
        int maxRetries,
        String relayInstanceId
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.claimTtl = claimTtl;
        this.maxRetries = maxRetries;
        this.relayInstanceId = relayInstanceId;
        this.publishSuccessCounter = meterRegistry.counter("outbox.publish.success");
        this.publishFailureCounter = meterRegistry.counter("outbox.publish.failure");
        this.deadLetterCounter = meterRegistry.counter("outbox.dead_letter.count");
        Gauge.builder("outbox.pending.count", () -> this.repository.countPending(this.clock.instant()))
            .register(meterRegistry);
    }

    public void relayPendingEvents() {
        Instant now = clock.instant();
        Instant staleClaimBefore = now.minus(claimTtl);
        List<OutboxEventRecord> pendingEvents = repository.findPendingForPublish(batchSize, now, staleClaimBefore);
        for (OutboxEventRecord event : pendingEvents) {
            relaySingleEvent(event, now, staleClaimBefore);
        }
    }

    private void relaySingleEvent(OutboxEventRecord event, Instant now, Instant staleClaimBefore) {
        boolean claimed = repository.claimForPublish(event.id(), now, staleClaimBefore, now, relayInstanceId);
        if (!claimed) {
            log.debug(
                "outbox_publish_skipped_not_claimed eventId={} aggregateId={} eventType={} retryCount={}",
                event.id(),
                event.aggregateId(),
                event.eventType(),
                event.retryCount()
            );
            return;
        }

        log.info(
            "outbox_publish_started eventId={} aggregateId={} eventType={} retryCount={}",
            event.id(),
            event.aggregateId(),
            event.eventType(),
            event.retryCount()
        );

        try {
            publisher.publish(event);

            Instant publishedAt = clock.instant();
            if (!repository.markPublished(event.id(), publishedAt)) {
                log.warn(
                    "outbox_mark_published_skipped_invalid_state eventId={} aggregateId={} eventType={}",
                    event.id(),
                    event.aggregateId(),
                    event.eventType()
                );
                return;
            }
            publishSuccessCounter.increment();

            log.info(
                "outbox_publish_succeeded eventId={} aggregateId={} eventType={} status={} publishedAt={}",
                event.id(),
                event.aggregateId(),
                event.eventType(),
                "PUBLISHED",
                publishedAt
            );
        } catch (RuntimeException exception) {
            int nextRetryCount = event.retryCount() + 1;
            String lastError = abbreviateError(exception);
            Instant failedAt = clock.instant();

            if (nextRetryCount > maxRetries) {
                boolean deadLettered = repository.markDeadLettered(event.id(), nextRetryCount, lastError, failedAt);
                if (deadLettered) {
                    deadLetterCounter.increment();
                    log.error(
                        "outbox_publish_dead_lettered eventId={} aggregateId={} eventType={} retryCount={} maxRetries={} deadLetteredAt={} lastError={}",
                        event.id(),
                        event.aggregateId(),
                        event.eventType(),
                        nextRetryCount,
                        maxRetries,
                        failedAt,
                        lastError,
                        exception
                    );
                } else {
                    log.warn(
                        "outbox_mark_dead_lettered_skipped_invalid_state eventId={} aggregateId={} eventType={} retryCount={}",
                        event.id(),
                        event.aggregateId(),
                        event.eventType(),
                        nextRetryCount,
                        exception
                    );
                }
                return;
            }

            Instant nextRetryAt = failedAt.plus(retryDelay);
            boolean failed = repository.markFailedForRetry(event.id(), nextRetryCount, nextRetryAt, lastError, failedAt);
            if (!failed) {
                log.warn(
                    "outbox_mark_failed_skipped_invalid_state eventId={} aggregateId={} eventType={} retryCount={}",
                    event.id(),
                    event.aggregateId(),
                    event.eventType(),
                    nextRetryCount,
                    exception
                );
                return;
            }
            publishFailureCounter.increment();

            log.warn(
                "outbox_publish_failed eventId={} aggregateId={} eventType={} retryCount={} nextRetryAt={}",
                event.id(),
                event.aggregateId(),
                event.eventType(),
                nextRetryCount,
                nextRetryAt,
                exception
            );
        }
    }

    private String abbreviateError(RuntimeException exception) {
        String message = exception.getMessage();
        String normalized = message == null ? exception.getClass().getSimpleName() : message;
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
