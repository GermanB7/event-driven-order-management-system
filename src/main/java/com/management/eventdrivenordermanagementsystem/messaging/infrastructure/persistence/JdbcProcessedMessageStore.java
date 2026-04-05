package com.management.eventdrivenordermanagementsystem.messaging.infrastructure.persistence;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class JdbcProcessedMessageStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedMessageStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean markProcessed(String consumerName, UUID messageId, Instant processedAt) {
        try {
            jdbcTemplate.update(
                """
                    insert into messaging.processed_messages (consumer_name, message_id, processed_at)
                    values (?, ?, ?)
                    """,
                consumerName,
                messageId,
                Timestamp.from(processedAt)
            );
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }
}

