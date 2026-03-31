package com.management.eventdrivenordermanagementsystem.outbox.integration;

import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;
import com.management.eventdrivenordermanagementsystem.outbox.infrastructure.persistence.JdbcOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(JdbcOutboxEventRepository.class)
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
        List<UUID> selectedIds = repository.findPendingForPublish(10, Instant.parse("2026-03-30T10:00:00Z"))
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

        repository.markPublished(pendingEventId, publishedAt);

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

        repository.markFailedForRetry(pendingEventId, 3, nextRetryAt);

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

        assertThat(status).isEqualTo("FAILED");
        assertThat(retryCount).isEqualTo(3);
        assertThat(dbNextRetryAt).isNotNull();
        assertThat(dbNextRetryAt.toInstant()).isEqualTo(nextRetryAt);
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


