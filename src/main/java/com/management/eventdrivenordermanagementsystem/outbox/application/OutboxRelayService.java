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
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;

    @Autowired
    public OutboxRelayService(
        OutboxEventRepository repository,
        OutboxEventPublisher publisher,
        MeterRegistry meterRegistry,
        @Value("${outbox.relay.batch-size:50}") int batchSize,
        @Value("${outbox.relay.retry-delay:PT30S}") Duration retryDelay
    ) {
        this(repository, publisher, meterRegistry, Clock.systemUTC(), batchSize, retryDelay);
    }

    OutboxRelayService(
        OutboxEventRepository repository,
        OutboxEventPublisher publisher,
        MeterRegistry meterRegistry,
        Clock clock,
        int batchSize,
        Duration retryDelay
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.publishSuccessCounter = meterRegistry.counter("outbox.publish.success");
        this.publishFailureCounter = meterRegistry.counter("outbox.publish.failure");
        Gauge.builder("outbox.pending.count", () -> this.repository.countPending(this.clock.instant()))
            .register(meterRegistry);
    }

    public void relayPendingEvents() {
        List<OutboxEventRecord> pendingEvents = repository.findPendingForPublish(batchSize, clock.instant());
        for (OutboxEventRecord event : pendingEvents) {
            relaySingleEvent(event);
        }
    }

    private void relaySingleEvent(OutboxEventRecord event) {
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
            repository.markPublished(event.id(), publishedAt);
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
            Instant nextRetryAt = clock.instant().plus(retryDelay);
            repository.markFailedForRetry(event.id(), nextRetryCount, nextRetryAt);
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
}
