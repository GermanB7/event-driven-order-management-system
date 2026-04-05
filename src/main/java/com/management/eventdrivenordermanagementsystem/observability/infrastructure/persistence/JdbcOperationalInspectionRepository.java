package com.management.eventdrivenordermanagementsystem.observability.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.FailedAsyncOperationView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.OperationalSummaryView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowStatusAggregateView;
import com.management.eventdrivenordermanagementsystem.observability.application.dto.WorkflowTimelineEventView;
import com.management.eventdrivenordermanagementsystem.observability.application.port.OperationalInspectionRepository;
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
public class JdbcOperationalInspectionRepository implements OperationalInspectionRepository {

    private static final String COLUMN_NEXT_RETRY_AT = "next_retry_at";
    private static final String COLUMN_WORKFLOW_ID = "workflowId";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOperationalInspectionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WorkflowTimelineEventView> findEventsByWorkflowId(String workflowId) {
        return jdbcTemplate.query(
            """
                select id, event_type, headers, status, retry_count, occurred_at, published_at, next_retry_at
                from outbox.outbox_event
                order by occurred_at asc, id asc
                """,
            new WorkflowTimelineRowMapper(objectMapper)
        ).stream()
            .filter(event -> workflowId.equals(event.workflowId()))
            .toList();
    }

    @Override
    public List<WorkflowTimelineEventView> findEventsByEventId(UUID eventId) {
        return jdbcTemplate.query(
            """
                select id, event_type, headers, status, retry_count, occurred_at, published_at, next_retry_at
                from outbox.outbox_event
                where id = ?
                """,
            new WorkflowTimelineRowMapper(objectMapper),
            eventId
        );
    }

    @Override
    public List<FailedAsyncOperationView> findFailedOperations() {
        return jdbcTemplate.query(
            """
                select id, event_type, aggregate_id, headers, status, retry_count, next_retry_at, occurred_at,
                       claimed_by, last_error, dead_lettered_at, replay_count, replayed_at, replayed_by
                from outbox.outbox_event
                where status in ('FAILED', 'DEAD_LETTER')
                order by next_retry_at asc nulls last, occurred_at desc
                """,
            (rs, rowNum) -> mapToFailedOperation(rs)
        );
    }

    @Override
    public List<FailedAsyncOperationView> findFailedOperationsByOrderId(UUID orderId) {
        return jdbcTemplate.query(
            """
                select id, event_type, aggregate_id, headers, status, retry_count, next_retry_at, occurred_at,
                       claimed_by, last_error, dead_lettered_at, replay_count, replayed_at, replayed_by
                from outbox.outbox_event
                where aggregate_id = ? and status in ('FAILED', 'DEAD_LETTER')
                order by occurred_at desc
                """,
            (rs, rowNum) -> mapToFailedOperation(rs),
            orderId.toString()
        );
    }

    @Override
    public WorkflowStatusAggregateView findWorkflowStatusAggregate(UUID orderId) {
        String orderStatus = queryStatusOrDefault(
            "select status from orders.orders where id = ?",
            orderId,
            "UNKNOWN"
        );

        String inventoryStatus = queryStatusOrDefault(
            "select status from inventory.inventory_reservations where order_id = ? order by updated_at desc limit 1",
            orderId,
            "NOT_STARTED"
        );

        String paymentStatus = queryStatusOrDefault(
            "select status from payments.payments where order_id = ?",
            orderId,
            "NOT_STARTED"
        );

        String shipmentStatus = queryStatusOrDefault(
            "select status from shipping.shipments where order_id = ?",
            orderId,
            "NOT_STARTED"
        );

        long totalEventCount = queryCount(
            "select count(*) from outbox.outbox_event where aggregate_id = ?",
            orderId.toString()
        );

        long failedEventCount = queryCount(
            "select count(*) from outbox.outbox_event where aggregate_id = ? and status = 'FAILED'",
            orderId.toString()
        );

        long deadLetteredCount = queryCount(
            "select count(*) from outbox.outbox_event where aggregate_id = ? and status = 'DEAD_LETTER'",
            orderId.toString()
        );

        return new WorkflowStatusAggregateView(
            orderId.toString(),
            orderStatus,
            inventoryStatus,
            paymentStatus,
            shipmentStatus,
            totalEventCount,
            failedEventCount,
            deadLetteredCount
        );
    }

    @Override
    public OperationalSummaryView findOperationalSummary() {
        Long ordersCreated = jdbcTemplate.queryForObject(
            "select count(*) from orders.orders where status = 'CREATED'",
            Long.class
        );

        Long ordersConfirmed = jdbcTemplate.queryForObject(
            "select count(*) from orders.orders where status = 'CONFIRMED'",
            Long.class
        );

        Long ordersCancelled = jdbcTemplate.queryForObject(
            "select count(*) from orders.orders where status = 'CANCELLED'",
            Long.class
        );

        Long ordersFulfillmentRequested = jdbcTemplate.queryForObject(
            "select count(*) from orders.orders where status = 'FULFILLMENT_REQUESTED'",
            Long.class
        );

        Long pendingOutboxEvents = jdbcTemplate.queryForObject(
            "select count(*) from outbox.outbox_event where status = 'PENDING'",
            Long.class
        );

        Long failedOutboxEvents = jdbcTemplate.queryForObject(
            "select count(*) from outbox.outbox_event where status = 'FAILED'",
            Long.class
        );

        Long deadLetteredOutboxEvents = jdbcTemplate.queryForObject(
            "select count(*) from outbox.outbox_event where status = 'DEAD_LETTER'",
            Long.class
        );

        Long publishedEvents = jdbcTemplate.queryForObject(
            "select count(*) from outbox.outbox_event where status = 'PUBLISHED'",
            Long.class
        );

        return new OperationalSummaryView(
            ordersCreated == null ? 0 : ordersCreated,
            ordersConfirmed == null ? 0 : ordersConfirmed,
            ordersCancelled == null ? 0 : ordersCancelled,
            ordersFulfillmentRequested == null ? 0 : ordersFulfillmentRequested,
            pendingOutboxEvents == null ? 0 : pendingOutboxEvents,
            failedOutboxEvents == null ? 0 : failedOutboxEvents,
            deadLetteredOutboxEvents == null ? 0 : deadLetteredOutboxEvents,
            publishedEvents == null ? 0 : publishedEvents
        );
    }

    private FailedAsyncOperationView mapToFailedOperation(ResultSet rs) throws SQLException {
        JsonNode headers = parseHeaders(rs.getString("headers"));
        String status = rs.getString("status");
        boolean deadLettered = "DEAD_LETTER".equals(status);
        String failureReason = rs.getString("last_error");
        return new FailedAsyncOperationView(
            rs.getString("id"),
            rs.getString("event_type"),
            rs.getString("aggregate_id"),
            textOrNull(headers, COLUMN_WORKFLOW_ID),
            nullableOrFallback(rs.getString("claimed_by"), "outbox-relay"),
            rs.getInt("retry_count"),
            nullableOrFallback(failureReason, deadLettered ? "Dead-lettered by outbox relay" : "Retry scheduled"),
            status,
            deadLettered,
            rs.getInt("replay_count"),
            rs.getString("replayed_by"),
            toStringOrNull(rs.getTimestamp("replayed_at")),
            rs.getTimestamp("occurred_at").toString(),
            toStringOrNull(rs.getTimestamp(COLUMN_NEXT_RETRY_AT))
        );
    }

    private String queryStatusOrDefault(String query, UUID orderId, String fallbackStatus) {
        List<String> statuses = jdbcTemplate.query(
            query,
            (rs, rowNum) -> rs.getString(1),
            orderId
        );

        if (statuses.isEmpty()) {
            return fallbackStatus;
        }

        return nullableOrFallback(statuses.get(0), fallbackStatus);
    }

    private long queryCount(String query, String orderId) {
        return jdbcTemplate.queryForObject(query, (rs, rowNum) -> rs.getLong(1), orderId);
    }

    private String nullableOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String toStringOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toString();
    }

    private JsonNode parseHeaders(String rawHeaders) {
        try {
            return objectMapper.readTree(rawHeaders);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse outbox headers", exception);
        }
    }

    private static String textOrNull(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        return valueNode.isMissingNode() || valueNode.isNull() ? null : valueNode.asText();
    }

    private static String workflowIdOrNull(JsonNode node) {
        return textOrNull(node, COLUMN_WORKFLOW_ID);
    }

    private record WorkflowTimelineRowMapper(ObjectMapper objectMapper) implements RowMapper<WorkflowTimelineEventView> {

        @Override
        public WorkflowTimelineEventView mapRow(ResultSet rs, int rowNum) throws SQLException {
            JsonNode headers = parseHeaders(rs.getString("headers"));
            return new WorkflowTimelineEventView(
                rs.getObject("id", UUID.class),
                rs.getString("event_type"),
                workflowIdOrNull(headers),
                textOrNull(headers, "correlationId"),
                textOrNull(headers, "causationId"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                toInstant(rs.getTimestamp("occurred_at")),
                toInstant(rs.getTimestamp("published_at")),
                toInstant(rs.getTimestamp(COLUMN_NEXT_RETRY_AT))
            );
        }

        private JsonNode parseHeaders(String rawHeaders) {
            try {
                return objectMapper.readTree(rawHeaders);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to parse outbox headers", exception);
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



