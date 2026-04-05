package com.management.eventdrivenordermanagementsystem.outbox.integration;

import com.management.eventdrivenordermanagementsystem.outbox.application.OutboxRelayService;
import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventPublisher;
import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "outbox.relay.enabled=false",
    "outbox.relay.batch-size=10",
    "outbox.relay.retry-delay=PT0S",
    "outbox.relay.max-retries=2",
    "outbox.kafka.enabled=false"
})
@Sql(scripts = "classpath:sql/order-slice-schema.sql")
class OutboxRelayServiceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRelayService relayService;

    @Autowired
    private RecordingOutboxEventPublisher publisher;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        publisher.clear();
        publisher.failAllPublishes(false);
        eventId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into outbox.outbox_event
                (id, aggregate_id, aggregate_type, event_type, payload, headers, status, occurred_at, retry_count, next_retry_at)
                values (?, ?, ?, ?, ?, ?, 'PENDING', ?, 0, null)
                """,
            eventId,
            "order-100",
            "ORDER",
            "ORDER_CREATED",
            "{\"orderId\":\"order-100\"}",
            "{\"correlationId\":\"order-100\"}",
            Timestamp.from(Instant.parse("2026-03-30T10:00:00Z"))
        );
    }

    @Test
    void relayPendingEventsPublishesAndMarksEventAsPublished() {
        relayService.relayPendingEvents();

        assertThat(publisher.publishedEventIds()).containsExactly(eventId);

        String status = jdbcTemplate.queryForObject(
            "select status from outbox.outbox_event where id = ?",
            String.class,
            eventId
        );
        Timestamp publishedAt = jdbcTemplate.queryForObject(
            "select published_at from outbox.outbox_event where id = ?",
            Timestamp.class,
            eventId
        );

        assertThat(status).isEqualTo("PUBLISHED");
        assertThat(publishedAt).isNotNull();
    }

    @Test
    void relayPendingEventsRetriesAndDeadLettersAfterRetryExhaustion() {
        publisher.failAllPublishes(true);

        relayService.relayPendingEvents();
        relayService.relayPendingEvents();
        relayService.relayPendingEvents();

        String status = jdbcTemplate.queryForObject(
            "select status from outbox.outbox_event where id = ?",
            String.class,
            eventId
        );
        Integer retryCount = jdbcTemplate.queryForObject(
            "select retry_count from outbox.outbox_event where id = ?",
            Integer.class,
            eventId
        );
        Timestamp deadLetteredAt = jdbcTemplate.queryForObject(
            "select dead_lettered_at from outbox.outbox_event where id = ?",
            Timestamp.class,
            eventId
        );
        String lastError = jdbcTemplate.queryForObject(
            "select last_error from outbox.outbox_event where id = ?",
            String.class,
            eventId
        );

        assertThat(status).isEqualTo("DEAD_LETTER");
        assertThat(retryCount).isEqualTo(3);
        assertThat(deadLetteredAt).isNotNull();
        assertThat(lastError).contains("forced publish failure");
    }

    @TestConfiguration
    static class TestPublisherConfiguration {

        @Bean
        @Primary
        RecordingOutboxEventPublisher recordingOutboxEventPublisher() {
            return new RecordingOutboxEventPublisher();
        }
    }

    static class RecordingOutboxEventPublisher implements OutboxEventPublisher {

        private final List<UUID> publishedEventIds = new ArrayList<>();
        private volatile boolean failAllPublishes;

        @Override
        public void publish(OutboxEventRecord event) {
            if (failAllPublishes) {
                throw new IllegalStateException("forced publish failure");
            }
            publishedEventIds.add(event.id());
        }

        List<UUID> publishedEventIds() {
            return List.copyOf(publishedEventIds);
        }

        void clear() {
            publishedEventIds.clear();
        }

        void failAllPublishes(boolean failAllPublishes) {
            this.failAllPublishes = failAllPublishes;
        }
    }
}


