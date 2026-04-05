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
    public List<OutboxEventRecord> findPendingForPublish(int limit, Instant now, Instant staleClaimBefore) {
        return jdbcTemplate.query(
            """
                select id, aggregate_id, aggregate_type, event_type, payload, headers,
                       status, occurred_at, published_at, retry_count, next_retry_at
                from outbox.outbox_event
                where (
                        status in ('PENDING', 'FAILED')
                        and (next_retry_at is null or next_retry_at <= ?)
                      )
                   or (
                        status = 'IN_PROGRESS'
                        and claimed_at <= ?
                      )
                order by occurred_at asc
                limit ?
                """,
            OUTBOX_EVENT_ROW_MAPPER,
            Timestamp.from(now),
            Timestamp.from(staleClaimBefore),
            limit
        );
    }

    @Override
    public boolean claimForPublish(UUID eventId, Instant now, Instant staleClaimBefore, Instant claimedAt, String claimedBy) {
        int updatedRows = jdbcTemplate.update(
            """
                update outbox.outbox_event
                set status = 'IN_PROGRESS',
                    claimed_at = ?,
                    claimed_by = ?
                where id = ?
                  and (
                        (status in ('PENDING', 'FAILED') and (next_retry_at is null or next_retry_at <= ?))
                        or (status = 'IN_PROGRESS' and claimed_at <= ?)
                      )
                """,
            Timestamp.from(claimedAt),
            claimedBy,
            eventId,
            Timestamp.from(now),
            Timestamp.from(staleClaimBefore)
        );
        return updatedRows == 1;
    }

    @Override
    public boolean markPublished(UUID eventId, Instant publishedAt) {
        int updatedRows = jdbcTemplate.update(
            """
                update outbox.outbox_event
                set status = 'PUBLISHED',
                    published_at = ?,
                    next_retry_at = null,
                    claimed_at = null,
                    claimed_by = null
                where id = ?
                  and status = 'IN_PROGRESS'
                """,
            Timestamp.from(publishedAt),
            eventId
        );
        return updatedRows == 1;
    }

    @Override
    public boolean markFailedForRetry(UUID eventId, int retryCount, Instant nextRetryAt, String lastError, Instant lastFailedAt) {
        int updatedRows = jdbcTemplate.update(
            """
                update outbox.outbox_event
                set status = 'FAILED',
                    retry_count = ?,
                    next_retry_at = ?,
                    last_error = ?,
                    last_failed_at = ?,
                    claimed_at = null,
                    claimed_by = null
                where id = ?
                  and status = 'IN_PROGRESS'
                """,
            retryCount,
            Timestamp.from(nextRetryAt),
            lastError,
            Timestamp.from(lastFailedAt),
            eventId
        );
        return updatedRows == 1;
    }

    @Override
    public boolean markDeadLettered(UUID eventId, int retryCount, String lastError, Instant deadLetteredAt) {
        int updatedRows = jdbcTemplate.update(
            """
                update outbox.outbox_event
                set status = 'DEAD_LETTER',
                    retry_count = ?,
                    next_retry_at = null,
                    last_error = ?,
                    last_failed_at = ?,
                    dead_lettered_at = ?,
                    claimed_at = null,
                    claimed_by = null
                where id = ?
                  and status = 'IN_PROGRESS'
                """,
            retryCount,
            lastError,
            Timestamp.from(deadLetteredAt),
            Timestamp.from(deadLetteredAt),
            eventId
        );
        return updatedRows == 1;
    }

    @Override
    public boolean requestReplay(UUID eventId, Instant replayedAt, String replayedBy) {
        int updatedRows = jdbcTemplate.update(
            """
                update outbox.outbox_event
                set status = 'FAILED',
                    next_retry_at = ?,
                    replay_count = replay_count + 1,
                    replayed_at = ?,
                    replayed_by = ?,
                    claimed_at = null,
                    claimed_by = null
                where id = ?
                  and status in ('FAILED', 'DEAD_LETTER')
                """,
            Timestamp.from(replayedAt),
            Timestamp.from(replayedAt),
            replayedBy,
            eventId
        );
        return updatedRows == 1;
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
