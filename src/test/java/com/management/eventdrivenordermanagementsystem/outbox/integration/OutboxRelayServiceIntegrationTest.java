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

        @Override
        public void publish(OutboxEventRecord event) {
            publishedEventIds.add(event.id());
        }

        List<UUID> publishedEventIds() {
            return List.copyOf(publishedEventIds);
        }

        void clear() {
            publishedEventIds.clear();
        }
    }
}


