package com.management.eventdrivenordermanagementsystem.outbox.application;

import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventPublisher;
import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventRepository;
import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;
import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayServiceTest {

    private OutboxEventRepository repository;
    private OutboxEventPublisher publisher;
    private OutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        publisher = mock(OutboxEventPublisher.class);
        relayService = new OutboxRelayService(
            repository,
            publisher,
            new SimpleMeterRegistry(),
            Clock.fixed(Instant.parse("2026-03-30T10:00:00Z"), ZoneOffset.UTC),
            20,
            Duration.ofSeconds(45),
            Duration.ofSeconds(30),
            3,
            "test-relay"
        );
    }

    @Test
    void relayPendingEventsMarksEventAsPublishedWhenPublishSucceeds() {
        OutboxEventRecord event = eventRecord();
        when(repository.findPendingForPublish(eq(20), any(Instant.class), any(Instant.class))).thenReturn(List.of(event));
        when(repository.claimForPublish(eq(event.id()), any(Instant.class), any(Instant.class), any(Instant.class), eq("test-relay"))).thenReturn(true);
        when(repository.markPublished(eq(event.id()), eq(Instant.parse("2026-03-30T10:00:00Z")))).thenReturn(true);

        relayService.relayPendingEvents();

        verify(publisher).publish(event);
        verify(repository).markPublished(event.id(), Instant.parse("2026-03-30T10:00:00Z"));
        verify(repository, never()).markFailedForRetry(any(), any(Integer.class), any(), anyString(), any());
        verify(repository, never()).markDeadLettered(any(), any(Integer.class), anyString(), any());
    }

    @Test
    void relayPendingEventsMarksEventAsFailedForRetryWhenPublishFails() {
        OutboxEventRecord event = eventRecord();
        when(repository.findPendingForPublish(eq(20), any(Instant.class), any(Instant.class))).thenReturn(List.of(event));
        when(repository.claimForPublish(eq(event.id()), any(Instant.class), any(Instant.class), any(Instant.class), eq("test-relay"))).thenReturn(true);
        when(repository.markFailedForRetry(eq(event.id()), eq(1), eq(Instant.parse("2026-03-30T10:00:45Z")), anyString(), eq(Instant.parse("2026-03-30T10:00:00Z"))))
            .thenReturn(true);
        doThrow(new IllegalStateException("Kafka unavailable")).when(publisher).publish(event);

        relayService.relayPendingEvents();

        verify(repository, never()).markPublished(any(), any());
        verify(repository).markFailedForRetry(
            event.id(),
            event.retryCount() + 1,
            Instant.parse("2026-03-30T10:00:45Z"),
            "Kafka unavailable",
            Instant.parse("2026-03-30T10:00:00Z")
        );
        verify(repository, never()).markDeadLettered(any(), any(Integer.class), anyString(), any());
    }

    @Test
    void relayPendingEventsSkipsEventWhenClaimIsNotAcquired() {
        OutboxEventRecord event = eventRecord();
        when(repository.findPendingForPublish(eq(20), any(Instant.class), any(Instant.class))).thenReturn(List.of(event));
        when(repository.claimForPublish(eq(event.id()), any(Instant.class), any(Instant.class), any(Instant.class), eq("test-relay"))).thenReturn(false);

        relayService.relayPendingEvents();

        verify(publisher, never()).publish(any());
        verify(repository, never()).markPublished(any(), any());
        verify(repository, never()).markFailedForRetry(any(), any(Integer.class), any(), anyString(), any());
        verify(repository, never()).markDeadLettered(any(), any(Integer.class), anyString(), any());
    }

    @Test
    void relayPendingEventsMovesEventToDeadLetterWhenRetryIsExhausted() {
        OutboxEventRecord event = eventRecordWithRetryCount(3);
        when(repository.findPendingForPublish(eq(20), any(Instant.class), any(Instant.class))).thenReturn(List.of(event));
        when(repository.claimForPublish(eq(event.id()), any(Instant.class), any(Instant.class), any(Instant.class), eq("test-relay"))).thenReturn(true);
        when(repository.markDeadLettered(eq(event.id()), eq(4), eq("Kafka unavailable"), eq(Instant.parse("2026-03-30T10:00:00Z"))))
            .thenReturn(true);
        doThrow(new IllegalStateException("Kafka unavailable")).when(publisher).publish(event);

        relayService.relayPendingEvents();

        verify(repository, never()).markPublished(any(), any());
        verify(repository, never()).markFailedForRetry(any(), any(Integer.class), any(), anyString(), any());
        verify(repository).markDeadLettered(
            event.id(),
            4,
            "Kafka unavailable",
            Instant.parse("2026-03-30T10:00:00Z")
        );
    }

    private OutboxEventRecord eventRecord() {
        return new OutboxEventRecord(
            UUID.randomUUID(),
            "order-1",
            "ORDER",
            "ORDER_CREATED",
            "{\"orderId\":\"order-1\"}",
            "{\"correlationId\":\"order-1\"}",
            OutboxEventStatus.PENDING,
            Instant.parse("2026-03-30T09:59:59Z"),
            null,
            0,
            null
        );
    }

    private OutboxEventRecord eventRecordWithRetryCount(int retryCount) {
        return new OutboxEventRecord(
            UUID.randomUUID(),
            "order-1",
            "ORDER",
            "ORDER_CREATED",
            "{\"orderId\":\"order-1\"}",
            "{\"correlationId\":\"order-1\"}",
            OutboxEventStatus.FAILED,
            Instant.parse("2026-03-30T09:59:59Z"),
            null,
            retryCount,
            Instant.parse("2026-03-30T09:59:00Z")
        );
    }
}

