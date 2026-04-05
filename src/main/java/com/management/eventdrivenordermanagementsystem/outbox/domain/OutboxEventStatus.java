package com.management.eventdrivenordermanagementsystem.outbox.domain;

public enum OutboxEventStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED,
    FAILED,
    DEAD_LETTER
}

