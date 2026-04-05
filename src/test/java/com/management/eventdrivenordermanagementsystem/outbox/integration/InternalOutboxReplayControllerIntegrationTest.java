package com.management.eventdrivenordermanagementsystem.outbox.integration;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "outbox.relay.enabled=false",
    "outbox.kafka.enabled=false"
})
@Sql(scripts = "classpath:sql/order-slice-schema.sql")
class InternalOutboxReplayControllerIntegrationTest {

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
    void replayEndpointRequeuesEligibleDeadLetteredEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into outbox.outbox_event
                (id, aggregate_id, aggregate_type, event_type, payload, headers, status, occurred_at, retry_count, next_retry_at, last_error, dead_lettered_at)
                values (?, ?, ?, ?, ?, ?, 'DEAD_LETTER', ?, 3, null, ?, ?)
                """,
            eventId,
            "order-200",
            "ORDER",
            "ORDER_CREATED",
            "{}",
            "{\"workflowId\":\"wf-replay-1\"}",
            Timestamp.from(Instant.parse("2026-03-30T10:00:00Z")),
            "forced failure",
            Timestamp.from(Instant.parse("2026-03-30T10:05:00Z"))
        );

        mockMvc.perform(post("/internal/outbox/events/{eventId}/replay", eventId).param("requestedBy", "ops-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value(eventId.toString()))
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.requestedBy").value("ops-user"))
            .andExpect(jsonPath("$.outcome").value("REQUEUED"));

        String status = jdbcTemplate.queryForObject(
            "select status from outbox.outbox_event where id = ?",
            String.class,
            eventId
        );
        Integer replayCount = jdbcTemplate.queryForObject(
            "select replay_count from outbox.outbox_event where id = ?",
            Integer.class,
            eventId
        );
        String replayedBy = jdbcTemplate.queryForObject(
            "select replayed_by from outbox.outbox_event where id = ?",
            String.class,
            eventId
        );

        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(replayCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(replayedBy).isEqualTo("ops-user");
    }

    @Test
    void replayEndpointRejectsPublishedEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into outbox.outbox_event
                (id, aggregate_id, aggregate_type, event_type, payload, headers, status, occurred_at, retry_count, next_retry_at)
                values (?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, 0, null)
                """,
            eventId,
            "order-201",
            "ORDER",
            "ORDER_CREATED",
            "{}",
            "{\"workflowId\":\"wf-replay-2\"}",
            Timestamp.from(Instant.parse("2026-03-30T10:10:00Z"))
        );

        mockMvc.perform(post("/internal/outbox/events/{eventId}/replay", eventId).param("requestedBy", "ops-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.outcome").value("NOT_ELIGIBLE"));
    }

    @TestConfiguration
    static class KafkaTestConfig {

        @Bean
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, String> consumerFactory() {
            return mock(ConsumerFactory.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }
    }
}


