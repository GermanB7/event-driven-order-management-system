package com.management.eventdrivenordermanagementsystem.messaging.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
    UUID eventId,
    EventType eventType,
    String aggregateId,
    String aggregateType,
    String workflowId,
    String correlationId,
    String causationId,
    Instant occurredAt,
    int version,
    JsonNode payload
) {
}

