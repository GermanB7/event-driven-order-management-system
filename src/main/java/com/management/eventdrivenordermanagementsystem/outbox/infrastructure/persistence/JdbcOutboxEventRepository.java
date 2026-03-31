package com.management.eventdrivenordermanagementsystem.outbox.infrastructure.persistence;

import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventRepository;
import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;
import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {

    private static final RowMapper<OutboxEventRecord> OUTBOX_EVENT_ROW_MAPPER = new OutboxEventRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<OutboxEventRecord> findPendingForPublish(int limit, Instant now) {
        return jdbcTemplate.query(
            """
                select id, aggregate_id, aggregate_type, event_type, payload, headers,
                       status, occurred_at, published_at, retry_count, next_retry_at
                from outbox.outbox_event
                where status in ('PENDING', 'FAILED')
                  and (next_retry_at is null or next_retry_at <= ?)
                order by occurred_at asc
                limit ?
                """,
            OUTBOX_EVENT_ROW_MAPPER,
            Timestamp.from(now),
            limit
        );
    }

    @Override
    public void markPublished(UUID eventId, Instant publishedAt) {
        jdbcTemplate.update(
            """
                update outbox.outbox_event
                set status = 'PUBLISHED',
                    published_at = ?,
                    next_retry_at = null
                where id = ?
                """,
            Timestamp.from(publishedAt),
            eventId
        );
    }

    @Override
    public void markFailedForRetry(UUID eventId, int retryCount, Instant nextRetryAt) {
        jdbcTemplate.update(
            """
                update outbox.outbox_event
                set status = 'FAILED',
                    retry_count = ?,
                    next_retry_at = ?
                where id = ?
                  and status <> 'PUBLISHED'
                """,
            retryCount,
            Timestamp.from(nextRetryAt),
            eventId
        );
    }

    @Override
    public long countPending(Instant now) {
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from outbox.outbox_event
                where status in ('PENDING', 'FAILED')
                  and (next_retry_at is null or next_retry_at <= ?)
                """,
            Long.class,
            Timestamp.from(now)
        );
        return count == null ? 0L : count;
    }

    private static final class OutboxEventRowMapper implements RowMapper<OutboxEventRecord> {

        @Override
        public OutboxEventRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new OutboxEventRecord(
                rs.getObject("id", UUID.class),
                rs.getString("aggregate_id"),
                rs.getString("aggregate_type"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getString("headers"),
                OutboxEventStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("occurred_at")),
                toInstant(rs.getTimestamp("published_at")),
                rs.getInt("retry_count"),
                toInstant(rs.getTimestamp("next_retry_at"))
            );
        }

        private Instant toInstant(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}
