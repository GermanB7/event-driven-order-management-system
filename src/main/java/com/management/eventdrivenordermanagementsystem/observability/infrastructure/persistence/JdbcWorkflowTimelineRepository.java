package com.management.eventdrivenordermanagementsystem.observability.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowTimelineEventView;
import com.management.eventdrivenordermanagementsystem.observability.application.port.WorkflowTimelineRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcWorkflowTimelineRepository implements WorkflowTimelineRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkflowTimelineRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WorkflowTimelineEventView> findByOrderId(UUID orderId) {
        return jdbcTemplate.query(
            """
                select id, event_type, headers, status, retry_count, occurred_at, published_at, next_retry_at
                from outbox.outbox_event
                where aggregate_id = ?
                order by occurred_at asc, id asc
                """,
            new WorkflowTimelineRowMapper(objectMapper),
            orderId.toString()
        );
    }

    private record WorkflowTimelineRowMapper(ObjectMapper objectMapper) implements RowMapper<WorkflowTimelineEventView> {

        @Override
        public WorkflowTimelineEventView mapRow(ResultSet rs, int rowNum) throws SQLException {
            JsonNode headers = parseHeaders(rs.getString("headers"));
            return new WorkflowTimelineEventView(
                rs.getObject("id", UUID.class),
                rs.getString("event_type"),
                textOrNull(headers, "workflowId"),
                textOrNull(headers, "correlationId"),
                textOrNull(headers, "causationId"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                toInstant(rs.getTimestamp("occurred_at")),
                toInstant(rs.getTimestamp("published_at")),
                toInstant(rs.getTimestamp("next_retry_at"))
            );
        }

        private JsonNode parseHeaders(String rawHeaders) {
            try {
                return objectMapper.readTree(rawHeaders);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to parse outbox headers for workflow timeline", exception);
            }
        }

        private String textOrNull(JsonNode node, String fieldName) {
            JsonNode valueNode = node.path(fieldName);
            return valueNode.isMissingNode() || valueNode.isNull() ? null : valueNode.asText();
        }

        private Instant toInstant(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}

