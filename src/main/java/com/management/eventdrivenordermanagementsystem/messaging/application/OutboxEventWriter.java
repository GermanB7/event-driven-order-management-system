package com.management.eventdrivenordermanagementsystem.messaging.application;

import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;

public interface OutboxEventWriter {
    void write(EventEnvelope envelope);
}

