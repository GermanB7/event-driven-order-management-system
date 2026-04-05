package com.management.eventdrivenordermanagementsystem.outbox.application;

import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxReplayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxReplayService.class);

    private final OutboxEventRepository repository;
    private final Clock clock;
    private final String replayInstanceId;
    private final Counter replaySuccessCounter;
    private final Counter replayRejectedCounter;

    @Autowired
    public OutboxReplayService(
        OutboxEventRepository repository,
        MeterRegistry meterRegistry,
        @Value("${outbox.replay.instance-id:${spring.application.name:outbox-replay}}") String replayInstanceId
    ) {
        this(repository, meterRegistry, Clock.systemUTC(), replayInstanceId);
    }

    OutboxReplayService(
        OutboxEventRepository repository,
        MeterRegistry meterRegistry,
        Clock clock,
        String replayInstanceId
    ) {
        this.repository = repository;
        this.clock = clock;
        this.replayInstanceId = replayInstanceId;
        this.replaySuccessCounter = meterRegistry.counter("outbox.replay.success");
        this.replayRejectedCounter = meterRegistry.counter("outbox.replay.rejected");
    }

    @Transactional
    public OutboxReplayResult replay(UUID eventId, String requestedBy) {
        Instant replayedAt = clock.instant();
        boolean replayed = repository.requestReplay(eventId, replayedAt, requestedBy);

        if (replayed) {
            replaySuccessCounter.increment();
            log.info(
                "outbox_replay_requested eventId={} requestedBy={} replayInstanceId={} replayedAt={}",
                eventId,
                requestedBy,
                replayInstanceId,
                replayedAt
            );
            return new OutboxReplayResult(eventId, true, requestedBy, replayedAt, "REQUEUED");
        }

        replayRejectedCounter.increment();
        log.warn(
            "outbox_replay_rejected eventId={} requestedBy={} replayInstanceId={} replayedAt={}",
            eventId,
            requestedBy,
            replayInstanceId,
            replayedAt
        );
        return new OutboxReplayResult(eventId, false, requestedBy, replayedAt, "NOT_ELIGIBLE");
    }

    public record OutboxReplayResult(UUID eventId, boolean replayed, String requestedBy, Instant replayedAt, String outcome) {
    }
}

