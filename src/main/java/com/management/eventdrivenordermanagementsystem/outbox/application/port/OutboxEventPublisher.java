package com.management.eventdrivenordermanagementsystem.outbox.application.port;

import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;

public interface OutboxEventPublisher {

    void publish(OutboxEventRecord event);
}

