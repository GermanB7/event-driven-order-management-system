package com.management.eventdrivenordermanagementsystem.outbox.application.dto;

import java.time.Instant;
import java.util.UUID;

public record OutboxReplayResponseView(
    UUID eventId,
    boolean replayed,
    String requestedBy,
    Instant replayedAt,
    String outcome
) {
}

