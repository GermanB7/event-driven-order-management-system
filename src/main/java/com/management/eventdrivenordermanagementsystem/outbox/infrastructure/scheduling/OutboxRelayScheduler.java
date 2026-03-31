package com.management.eventdrivenordermanagementsystem.outbox.infrastructure.scheduling;

import com.management.eventdrivenordermanagementsystem.outbox.application.OutboxRelayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelayService relayService;

    public OutboxRelayScheduler(OutboxRelayService relayService) {
        this.relayService = relayService;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:5000}")
    public void relayPendingEvents() {
        relayService.relayPendingEvents();
    }
}

