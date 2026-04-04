package com.management.eventdrivenordermanagementsystem.observability.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

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
@Sql(scripts = "classpath:sql/order-slice-schema.sql")
class InternalWorkflowInspectionIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

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

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void returnsTimelineForOrderOrderedByOccurrenceWithWorkflowMetadata() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID createdEventId = UUID.randomUUID();
        UUID inventoryEventId = UUID.randomUUID();

        insertOutboxEvent(
            inventoryEventId,
            orderId,
            "INVENTORY_RESERVATION_REQUESTED",
            "FAILED",
            2,
            Instant.parse("2026-04-02T10:00:02Z"),
            null,
            Instant.parse("2026-04-02T10:05:00Z"),
            "wf-777",
            "corr-777",
            createdEventId.toString()
        );

        insertOutboxEvent(
            createdEventId,
            orderId,
            "ORDER_CREATED",
            "PUBLISHED",
            0,
            Instant.parse("2026-04-02T10:00:00Z"),
            Instant.parse("2026-04-02T10:00:01Z"),
            null,
            "wf-777",
            "corr-777",
            null
        );

        mockMvc.perform(get("/internal/orders/{orderId}/workflow", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.workflowId").value("wf-777"))
            .andExpect(jsonPath("$.currentWorkflowState").value("INVENTORY_RESERVATION_REQUESTED"))
            .andExpect(jsonPath("$.timeline.length()").value(2))
            .andExpect(jsonPath("$.timeline[0].eventType").value("ORDER_CREATED"))
            .andExpect(jsonPath("$.timeline[0].publishStatus").value("PUBLISHED"))
            .andExpect(jsonPath("$.timeline[1].eventType").value("INVENTORY_RESERVATION_REQUESTED"))
            .andExpect(jsonPath("$.timeline[1].retryCount").value(2));
    }

    @Test
    void returnsNoEventsWorkflowWhenOrderHasNoTimeline() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get("/internal/orders/{orderId}/workflow", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.currentWorkflowState").value("NO_EVENTS"))
            .andExpect(jsonPath("$.timeline.length()").value(0));
    }

    private void insertOutboxEvent(
        UUID eventId,
        UUID orderId,
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
            orderId.toString(),
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
}



