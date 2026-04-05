package com.management.eventdrivenordermanagementsystem.observability.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "outbox.relay.enabled=false",
    "outbox.kafka.enabled=false"
})
@Sql(scripts = {
    "classpath:sql/order-slice-schema.sql",
    "classpath:sql/observability-operational-schema.sql"
})
class InternalOperationalEndpointsIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void getFailuresEndpointReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/internal/failures"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getFailuresEndpointReturnsRealFailureMetadata() throws Exception {
        UUID eventId = UUID.randomUUID();
        insertOutboxEvent(
            eventId,
            UUID.randomUUID().toString(),
            "PAYMENT_AUTHORIZATION_REQUESTED",
            "DEAD_LETTER",
            4,
            Instant.parse("2026-04-04T09:30:00Z"),
            null,
            null,
            "wf-fail-1",
            "corr-fail-1",
            null
        );
        updateFailureMetadata(eventId, "relay-instance-a", "Kafka publish timeout");

        mockMvc.perform(get("/internal/failures"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].eventId").value(eventId.toString()))
            .andExpect(jsonPath("$[0].deadLettered").value(true))
            .andExpect(jsonPath("$[0].consumerName").value("relay-instance-a"))
            .andExpect(jsonPath("$[0].failureReason").value("Kafka publish timeout"));
    }

    @Test
    void getSummaryEndpointReturnsOperationalMetrics() throws Exception {
        mockMvc.perform(get("/internal/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalOrdersCreated").isNumber())
            .andExpect(jsonPath("$.pendingOutboxEventCount").isNumber())
            .andExpect(jsonPath("$.failedOutboxEventCount").isNumber())
            .andExpect(jsonPath("$.totalEventsPublished").isNumber());
    }

    @Test
    void statusEndpointHandlesPartialWorkflowWithFallbackStates() throws Exception {
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, "CREATED");

        mockMvc.perform(get("/internal/orders/{orderId}/status", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.orderStatus").value("CREATED"))
            .andExpect(jsonPath("$.inventoryReservationStatus").value("NOT_STARTED"))
            .andExpect(jsonPath("$.paymentStatus").value("NOT_STARTED"))
            .andExpect(jsonPath("$.shipmentStatus").value("NOT_STARTED"))
            .andExpect(jsonPath("$.totalEventCount").value(0))
            .andExpect(jsonPath("$.failedEventCount").value(0))
            .andExpect(jsonPath("$.deadLetteredEventCount").value(0));
    }

    @Test
    void workflowsEndpointFindsEventsUsingRealWorkflowId() throws Exception {
        UUID orderOne = UUID.randomUUID();
        UUID orderTwo = UUID.randomUUID();

        insertOutboxEvent(
            UUID.randomUUID(),
            orderOne.toString(),
            "ORDER_CREATED",
            "PUBLISHED",
            0,
            Instant.parse("2026-04-04T10:00:00Z"),
            Instant.parse("2026-04-04T10:00:02Z"),
            null,
            "wf-123",
            "corr-1",
            null
        );

        insertOutboxEvent(
            UUID.randomUUID(),
            orderOne.toString(),
            "INVENTORY_RESERVATION_REQUESTED",
            "FAILED",
            1,
            Instant.parse("2026-04-04T10:00:03Z"),
            null,
            Instant.parse("2026-04-04T10:05:00Z"),
            "wf-123",
            "corr-1",
            null
        );

        insertOutboxEvent(
            UUID.randomUUID(),
            orderTwo.toString(),
            "ORDER_CREATED",
            "PUBLISHED",
            0,
            Instant.parse("2026-04-04T10:01:00Z"),
            Instant.parse("2026-04-04T10:01:02Z"),
            null,
            "wf-999",
            "corr-2",
            null
        );

        mockMvc.perform(get("/internal/workflows").param("workflowId", "wf-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].workflowId").value("wf-123"))
            .andExpect(jsonPath("$[1].workflowId").value("wf-123"));
    }

    @Test
    void summaryEndpointReturnsRealDeadLetterCounts() throws Exception {
        insertOutboxEvent(
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            "ORDER_CREATED",
            "DEAD_LETTER",
            5,
            Instant.parse("2026-04-04T09:00:00Z"),
            null,
            null,
            "wf-dead-1",
            "corr-3",
            null
        );

        insertOutboxEvent(
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            "PAYMENT_AUTHORIZATION_REQUESTED",
            "FAILED",
            2,
            Instant.parse("2026-04-04T09:10:00Z"),
            null,
            Instant.parse("2026-04-04T09:11:00Z"),
            "wf-failed-1",
            "corr-4",
            null
        );

        insertOutboxEvent(
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            "ORDER_CREATED",
            "PENDING",
            0,
            Instant.parse("2026-04-04T09:20:00Z"),
            null,
            null,
            "wf-pending-1",
            "corr-5",
            null
        );

        mockMvc.perform(get("/internal/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deadLetteredEventCount").value(1))
            .andExpect(jsonPath("$.failedOutboxEventCount").value(1))
            .andExpect(jsonPath("$.pendingOutboxEventCount").value(1));
    }

    @TestConfiguration
    static class KafkaTestConfig {

        @Bean
        ConsumerFactory<String, String> consumerFactory() {
            return mock(ConsumerFactory.class);
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }
    }

    private void insertOutboxEvent(
        UUID eventId,
        String orderId,
        String eventType,
        String status,
        int retryCount,
        Instant occurredAt,
        Instant publishedAt,
        Instant nextRetryAt,
        String workflowId,
        String correlationId,
        String causationId
    ) {
        String headers = """
            {"eventId":"%s","workflowId":"%s","correlationId":"%s","causationId":%s,"version":1}
            """.formatted(
            eventId,
            workflowId,
            correlationId,
            causationId == null ? "null" : "\"" + causationId + "\""
        );

        jdbcTemplate.update(
            """
                insert into outbox.outbox_event
                (id, aggregate_id, aggregate_type, event_type, payload, headers, status, occurred_at, published_at, retry_count, next_retry_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            eventId,
            orderId,
            "ORDER",
            eventType,
            "{}",
            headers,
            status,
            Timestamp.from(occurredAt),
            publishedAt == null ? null : Timestamp.from(publishedAt),
            retryCount,
            nextRetryAt == null ? null : Timestamp.from(nextRetryAt)
        );
    }

    private void insertOrder(UUID orderId, String status) {
        Instant now = Instant.parse("2026-04-04T08:00:00Z");
        jdbcTemplate.update(
            """
                insert into orders.orders
                (id, customer_id, status, currency, total_amount, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
            orderId,
            UUID.randomUUID(),
            status,
            "USD",
            BigDecimal.valueOf(100.00),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private void updateFailureMetadata(UUID eventId, String claimedBy, String lastError) {
        jdbcTemplate.update(
            """
                update outbox.outbox_event
                set claimed_by = ?,
                    last_error = ?
                where id = ?
                """,
            claimedBy,
            lastError,
            eventId
        );
    }
}


