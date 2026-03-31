package com.management.eventdrivenordermanagementsystem.messaging.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcOutboxEventWriter implements OutboxEventWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOutboxEventWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(EventEnvelope envelope) {
        ObjectNode headers = objectMapper.createObjectNode();
        headers.put("eventId", envelope.eventId().toString());
        headers.put("version", envelope.version());
        headers.put("workflowId", envelope.workflowId());
        headers.put("correlationId", envelope.correlationId());
        headers.put("causationId", envelope.causationId());

        jdbcTemplate.update(
            """
                insert into outbox.outbox_event
                (id, aggregate_id, aggregate_type, event_type, payload, headers, status, occurred_at, published_at, retry_count, next_retry_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, null, 0, null)
                """,
            envelope.eventId(),
            envelope.aggregateId(),
            envelope.aggregateType(),
            envelope.eventType().name(),
            serialize(envelope.payload()),
            serialize(headers),
            "PENDING",
            envelope.occurredAt()
        );
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize outbox payload", exception);
        }
    }
}

