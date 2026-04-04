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

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOperationalInspectionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WorkflowTimelineEventView> findEventsByWorkflowId(String workflowId) {
        // In production (PostgreSQL), use: headers ->> 'workflowId' = ?
        // For now, return empty list as H2 has limited JSON support
        return List.of();
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
                select id, event_type, aggregate_id, headers, status, retry_count, next_retry_at, occurred_at
                from outbox.outbox_event
                where status in ('FAILED')
                order by next_retry_at asc nulls last, occurred_at desc
                """,
            (rs, rowNum) -> mapToFailedOperation(rs, null)
        );
    }

    @Override
    public List<FailedAsyncOperationView> findFailedOperationsByOrderId(UUID orderId) {
        return jdbcTemplate.query(
            """
                select id, event_type, aggregate_id, headers, status, retry_count, next_retry_at, occurred_at
                from outbox.outbox_event
                where aggregate_id = ? and status in ('FAILED')
                order by occurred_at desc
                """,
            (rs, rowNum) -> mapToFailedOperation(rs, null),
            orderId.toString()
        );
    }

    @Override
    public WorkflowStatusAggregateView findWorkflowStatusAggregate(UUID orderId) {
        String orderStatus = jdbcTemplate.queryForObject(
            "select status from orders.orders where id = ?",
            String.class,
            orderId
        );

        String inventoryStatus = jdbcTemplate.queryForObject(
            "select status from inventory.inventory_reservations where order_id = ? limit 1",
            String.class,
            orderId
        );

        String paymentStatus = jdbcTemplate.queryForObject(
            "select status from payments.payments where order_id = ?",
            String.class,
            orderId
        );

        String shipmentStatus = jdbcTemplate.queryForObject(
            "select status from shipping.shipments where order_id = ?",
            String.class,
            orderId
        );

        Long totalEventCount = jdbcTemplate.queryForObject(
            "select count(*) from outbox.outbox_event where aggregate_id = ?",
            Long.class,
            orderId.toString()
        );

        Long failedEventCount = jdbcTemplate.queryForObject(
            "select count(*) from outbox.outbox_event where aggregate_id = ? and status = 'FAILED'",
            Long.class,
            orderId.toString()
        );

        Long deadLetteredCount = 0L;

        return new WorkflowStatusAggregateView(
            orderId.toString(),
            orderStatus,
            inventoryStatus,
            paymentStatus,
            shipmentStatus,
            totalEventCount == null ? 0 : totalEventCount,
            failedEventCount == null ? 0 : failedEventCount,
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
            0L,
            publishedEvents == null ? 0 : publishedEvents
        );
    }

    private FailedAsyncOperationView mapToFailedOperation(ResultSet rs, Object ignored) throws SQLException {
        JsonNode headers = parseHeaders(rs.getString("headers"));
        return new FailedAsyncOperationView(
            rs.getString("id"),
            rs.getString("event_type"),
            rs.getString("aggregate_id"),
            textOrNull(headers, "workflowId"),
            "outbox-relay",
            rs.getInt("retry_count"),
            "Retry scheduled",
            rs.getString("status"),
            false,
            rs.getTimestamp("occurred_at").toString(),
            rs.getTimestamp("next_retry_at") == null ? null : rs.getTimestamp("next_retry_at").toString()
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



