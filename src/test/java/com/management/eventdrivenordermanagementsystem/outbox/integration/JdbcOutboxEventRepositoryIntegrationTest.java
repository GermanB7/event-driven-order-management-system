package com.management.eventdrivenordermanagementsystem.outbox.integration;

import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;
import com.management.eventdrivenordermanagementsystem.outbox.infrastructure.persistence.JdbcOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "outbox.relay.enabled=false",
    "outbox.kafka.enabled=false"
})
@Sql(scripts = "classpath:sql/order-slice-schema.sql")
class JdbcOutboxEventRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcOutboxEventRepository repository;

    private UUID pendingEventId;
    private UUID failedReadyEventId;
    private UUID failedFutureEventId;

    @BeforeEach
    void setUp() {
        pendingEventId = UUID.randomUUID();
        failedReadyEventId = UUID.randomUUID();
        failedFutureEventId = UUID.randomUUID();

        insertOutboxEvent(pendingEventId, "PENDING", 0, null, Instant.parse("2026-03-30T09:00:00Z"));
        insertOutboxEvent(failedReadyEventId, "FAILED", 1, Instant.parse("2026-03-30T09:30:00Z"), Instant.parse("2026-03-30T09:01:00Z"));
        insertOutboxEvent(failedFutureEventId, "FAILED", 2, Instant.parse("2026-03-30T11:00:00Z"), Instant.parse("2026-03-30T09:02:00Z"));
    }

    @Test
    void findPendingForPublishReturnsPendingAndRetryReadyEvents() {
        List<UUID> selectedIds = repository.findPendingForPublish(
                10,
                Instant.parse("2026-03-30T10:00:00Z"),
                Instant.parse("2026-03-30T09:59:30Z")
            )
            .stream()
            .map(OutboxEventRecord::id)
            .toList();

        assertThat(selectedIds)
            .containsExactly(pendingEventId, failedReadyEventId)
            .doesNotContain(failedFutureEventId);
    }

    @Test
    void markPublishedUpdatesStatusAndPublishedAt() {
        Instant publishedAt = Instant.parse("2026-03-30T10:10:00Z");
        boolean claimed = repository.claimForPublish(
            pendingEventId,
            Instant.parse("2026-03-30T10:00:00Z"),
            Instant.parse("2026-03-30T09:59:30Z"),
            Instant.parse("2026-03-30T10:00:01Z"),
            "relay-a"
        );
        assertThat(claimed).isTrue();

        boolean published = repository.markPublished(pendingEventId, publishedAt);
        assertThat(published).isTrue();

        String status = jdbcTemplate.queryForObject(
            "select status from outbox.outbox_event where id = ?",
            String.class,
            pendingEventId
        );
        Timestamp dbPublishedAt = jdbcTemplate.queryForObject(
            "select published_at from outbox.outbox_event where id = ?",
            Timestamp.class,
            pendingEventId
        );

        assertThat(status).isEqualTo("PUBLISHED");
        assertThat(dbPublishedAt).isNotNull();
        assertThat(dbPublishedAt.toInstant()).isEqualTo(publishedAt);
    }

    @Test
    void markFailedForRetryUpdatesRetryMetadata() {
        Instant nextRetryAt = Instant.parse("2026-03-30T10:45:00Z");
        Instant failedAt = Instant.parse("2026-03-30T10:00:10Z");
        boolean claimed = repository.claimForPublish(
            pendingEventId,
            Instant.parse("2026-03-30T10:00:00Z"),
            Instant.parse("2026-03-30T09:59:30Z"),
            Instant.parse("2026-03-30T10:00:01Z"),
            "relay-a"
        );
        assertThat(claimed).isTrue();

        boolean failed = repository.markFailedForRetry(pendingEventId, 3, nextRetryAt, "Kafka timeout", failedAt);
        assertThat(failed).isTrue();

        String status = jdbcTemplate.queryForObject(
            "select status from outbox.outbox_event where id = ?",
            String.class,
            pendingEventId
        );
        Integer retryCount = jdbcTemplate.queryForObject(
            "select retry_count from outbox.outbox_event where id = ?",
            Integer.class,
            pendingEventId
        );
        Timestamp dbNextRetryAt = jdbcTemplate.queryForObject(
            "select next_retry_at from outbox.outbox_event where id = ?",
            Timestamp.class,
            pendingEventId
        );
        String lastError = jdbcTemplate.queryForObject(
            "select last_error from outbox.outbox_event where id = ?",
            String.class,
            pendingEventId
        );
        Timestamp dbLastFailedAt = jdbcTemplate.queryForObject(
            "select last_failed_at from outbox.outbox_event where id = ?",
            Timestamp.class,
            pendingEventId
        );

        assertThat(status).isEqualTo("FAILED");
        assertThat(retryCount).isEqualTo(3);
        assertThat(dbNextRetryAt).isNotNull();
        assertThat(dbNextRetryAt.toInstant()).isEqualTo(nextRetryAt);
        assertThat(lastError).isEqualTo("Kafka timeout");
        assertThat(dbLastFailedAt).isNotNull();
        assertThat(dbLastFailedAt.toInstant()).isEqualTo(failedAt);
    }

    @Test
    void claimForPublishIsAtomicAndOnlyOneCallerCanClaim() {
        Instant now = Instant.parse("2026-03-30T10:00:00Z");
        Instant claimedAt = Instant.parse("2026-03-30T10:00:01Z");

        boolean firstClaim = repository.claimForPublish(pendingEventId, now, now.minusSeconds(30), claimedAt, "relay-a");
        boolean secondClaim = repository.claimForPublish(pendingEventId, now, now.minusSeconds(30), claimedAt, "relay-b");

        assertThat(firstClaim).isTrue();
        assertThat(secondClaim).isFalse();
    }

    @Test
    void markPublishedFailsForInvalidStateTransitionWithoutClaim() {
        boolean published = repository.markPublished(pendingEventId, Instant.parse("2026-03-30T10:10:00Z"));

        assertThat(published).isFalse();

        String status = jdbcTemplate.queryForObject(
            "select status from outbox.outbox_event where id = ?",
            String.class,
            pendingEventId
        );
        assertThat(status).isEqualTo("PENDING");
    }

    @Test
    void markDeadLetteredUpdatesTerminalStateAndMetadata() {
        Instant now = Instant.parse("2026-03-30T10:00:00Z");
        boolean claimed = repository.claimForPublish(pendingEventId, now, now.minusSeconds(30), now, "relay-a");
        assertThat(claimed).isTrue();

        boolean deadLettered = repository.markDeadLettered(
            pendingEventId,
            6,
            "Kafka unavailable",
            Instant.parse("2026-03-30T10:00:05Z")
        );
        assertThat(deadLettered).isTrue();

        String status = jdbcTemplate.queryForObject(
            "select status from outbox.outbox_event where id = ?",
            String.class,
            pendingEventId
        );
        Integer retryCount = jdbcTemplate.queryForObject(
            "select retry_count from outbox.outbox_event where id = ?",
            Integer.class,
            pendingEventId
        );
        String lastError = jdbcTemplate.queryForObject(
            "select last_error from outbox.outbox_event where id = ?",
            String.class,
            pendingEventId
        );
        Timestamp deadLetteredAt = jdbcTemplate.queryForObject(
            "select dead_lettered_at from outbox.outbox_event where id = ?",
            Timestamp.class,
            pendingEventId
        );

        assertThat(status).isEqualTo("DEAD_LETTER");
        assertThat(retryCount).isEqualTo(6);
        assertThat(lastError).isEqualTo("Kafka unavailable");
        assertThat(deadLetteredAt).isNotNull();
    }

    @Test
    void requestReplayRequeuesFailedEventAndPersistsReplayMetadata() {
        Instant replayedAt = Instant.parse("2026-03-30T10:20:00Z");
        boolean replayed = repository.requestReplay(pendingEventId, replayedAt, "internal-ops");

        assertThat(replayed).isFalse();

        boolean eligibleReplay = repository.requestReplay(failedReadyEventId, replayedAt, "internal-ops");
        assertThat(eligibleReplay).isTrue();

        String status = jdbcTemplate.queryForObject(
            "select status from outbox.outbox_event where id = ?",
            String.class,
            failedReadyEventId
        );
        Integer replayCount = jdbcTemplate.queryForObject(
            "select replay_count from outbox.outbox_event where id = ?",
            Integer.class,
            failedReadyEventId
        );
        Timestamp nextRetryAt = jdbcTemplate.queryForObject(
            "select next_retry_at from outbox.outbox_event where id = ?",
            Timestamp.class,
            failedReadyEventId
        );
        String replayedBy = jdbcTemplate.queryForObject(
            "select replayed_by from outbox.outbox_event where id = ?",
            String.class,
            failedReadyEventId
        );
        Timestamp dbReplayedAt = jdbcTemplate.queryForObject(
            "select replayed_at from outbox.outbox_event where id = ?",
            Timestamp.class,
            failedReadyEventId
        );

        assertThat(status).isEqualTo("FAILED");
        assertThat(replayCount).isEqualTo(1);
        assertThat(nextRetryAt).isNotNull();
        assertThat(nextRetryAt.toInstant()).isEqualTo(replayedAt);
        assertThat(replayedBy).isEqualTo("internal-ops");
        assertThat(dbReplayedAt).isNotNull();
        assertThat(dbReplayedAt.toInstant()).isEqualTo(replayedAt);
    }

    @Test
    void requestReplayRejectsPublishedEvent() {
        Instant now = Instant.parse("2026-03-30T10:20:00Z");
        boolean claimed = repository.claimForPublish(
            pendingEventId,
            now,
            now.minusSeconds(30),
            now,
            "relay-a"
        );
        assertThat(claimed).isTrue();
        boolean published = repository.markPublished(pendingEventId, now.plusSeconds(1));
        assertThat(published).isTrue();

        boolean replayed = repository.requestReplay(pendingEventId, now.plusSeconds(5), "internal-ops");

        assertThat(replayed).isFalse();
    }

    @Test
    void claimForPublishCanReclaimStaleInProgressEvent() {
        Instant now = Instant.parse("2026-03-30T10:00:00Z");
        boolean initialClaim = repository.claimForPublish(
            pendingEventId,
            now,
            now.minusSeconds(30),
            Instant.parse("2026-03-30T09:59:00Z"),
            "relay-a"
        );
        assertThat(initialClaim).isTrue();

        boolean reclaimed = repository.claimForPublish(
            pendingEventId,
            now,
            now.minusSeconds(30),
            now,
            "relay-b"
        );

        assertThat(reclaimed).isTrue();
    }

    private void insertOutboxEvent(UUID id, String status, int retryCount, Instant nextRetryAt, Instant occurredAt) {
        jdbcTemplate.update(
            """
                insert into outbox.outbox_event
                (id, aggregate_id, aggregate_type, event_type, payload, headers, status, occurred_at, retry_count, next_retry_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            id,
            "order-aggregate",
            "ORDER",
            "ORDER_CREATED",
            "{\"orderId\":\"order-aggregate\"}",
            "{\"correlationId\":\"order-aggregate\"}",
            status,
            Timestamp.from(occurredAt),
            retryCount,
            nextRetryAt == null ? null : Timestamp.from(nextRetryAt)
        );
    }
}


