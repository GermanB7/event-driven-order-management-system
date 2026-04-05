package com.management.eventdrivenordermanagementsystem.outbox.application;

import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxReplayServiceTest {

    private OutboxEventRepository repository;
    private OutboxReplayService replayService;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        replayService = new OutboxReplayService(
            repository,
            new SimpleMeterRegistry(),
            Clock.fixed(Instant.parse("2026-03-30T10:00:00Z"), ZoneOffset.UTC),
            "test-replay"
        );
    }

    @Test
    void replayReturnsRequeuedWhenRepositoryAcceptsEligibleEvent() {
        UUID eventId = UUID.randomUUID();
        when(repository.requestReplay(eventId, Instant.parse("2026-03-30T10:00:00Z"), "ops-user")).thenReturn(true);

        OutboxReplayService.OutboxReplayResult result = replayService.replay(eventId, "ops-user");

        assertThat(result.replayed()).isTrue();
        assertThat(result.outcome()).isEqualTo("REQUEUED");
        verify(repository).requestReplay(eventId, Instant.parse("2026-03-30T10:00:00Z"), "ops-user");
    }

    @Test
    void replayReturnsNotEligibleWhenRepositoryRejectsEvent() {
        UUID eventId = UUID.randomUUID();
        when(repository.requestReplay(eventId, Instant.parse("2026-03-30T10:00:00Z"), "ops-user")).thenReturn(false);

        OutboxReplayService.OutboxReplayResult result = replayService.replay(eventId, "ops-user");

        assertThat(result.replayed()).isFalse();
        assertThat(result.outcome()).isEqualTo("NOT_ELIGIBLE");
    }
}



