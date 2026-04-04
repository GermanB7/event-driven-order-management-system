package com.management.eventdrivenordermanagementsystem.observability.integration;

import com.management.eventdrivenordermanagementsystem.observability.application.dto.OperationalSummaryView;
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
    void getSummaryEndpointReturnsOperationalMetrics() throws Exception {
        mockMvc.perform(get("/internal/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalOrdersCreated").isNumber())
            .andExpect(jsonPath("$.pendingOutboxEventCount").isNumber())
            .andExpect(jsonPath("$.failedOutboxEventCount").isNumber())
            .andExpect(jsonPath("$.totalEventsPublished").isNumber());
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
}


