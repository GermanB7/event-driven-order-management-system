package com.management.eventdrivenordermanagementsystem.outbox.interfaces.http;

import com.management.eventdrivenordermanagementsystem.outbox.application.OutboxReplayService;
import com.management.eventdrivenordermanagementsystem.outbox.application.dto.OutboxReplayResponseView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/outbox")
public class InternalOutboxReplayController {

    private final OutboxReplayService outboxReplayService;

    public InternalOutboxReplayController(OutboxReplayService outboxReplayService) {
        this.outboxReplayService = outboxReplayService;
    }

    @PostMapping("/events/{eventId}/replay")
    public ResponseEntity<OutboxReplayResponseView> replayEvent(
        @PathVariable UUID eventId,
        @RequestParam(defaultValue = "internal-ops") String requestedBy
    ) {
        OutboxReplayService.OutboxReplayResult result = outboxReplayService.replay(eventId, requestedBy);
        return ResponseEntity.ok(
            new OutboxReplayResponseView(
                result.eventId(),
                result.replayed(),
                result.requestedBy(),
                result.replayedAt(),
                result.outcome()
            )
        );
    }
}

