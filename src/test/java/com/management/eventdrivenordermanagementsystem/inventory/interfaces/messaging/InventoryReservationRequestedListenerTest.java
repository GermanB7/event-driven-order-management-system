package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.inventory.application.ProcessInventoryReservationRequestedUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class InventoryReservationRequestedListenerTest {

    private ProcessInventoryReservationRequestedUseCase useCase;
    private InventoryReservationRequestedListener listener;

    @BeforeEach
    void setUp() {
        useCase = mock(ProcessInventoryReservationRequestedUseCase.class);
        listener = new InventoryReservationRequestedListener(
            new ObjectMapper(),
            useCase,
            new InventoryConsumerFailureClassifier(),
            new SimpleMeterRegistry()
        );
    }

    @Test
    void malformedPayloadIsClassifiedAsNonRetryable() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "key", "{invalid-json");
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", "INVENTORY_RESERVATION_REQUESTED".getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        assertThatThrownBy(() -> listener.onMessage(record))
            .isInstanceOf(NonRetryableInventoryMessageException.class);
    }

    @Test
    void transientUseCaseFailureIsClassifiedAsRetryable() {
        ConsumerRecord<String, String> record = validRecord();
        doThrow(new TransientDataAccessResourceException("db unavailable"))
            .when(useCase)
            .execute(any());

        assertThatThrownBy(() -> listener.onMessage(record))
            .isInstanceOf(RetryableInventoryMessageException.class);
    }

    private ConsumerRecord<String, String> validRecord() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>(
            "order-events",
            0,
            0L,
            orderId.toString(),
            """
                {
                  "orderId": "%s",
                  "workflowId": "wf-200",
                  "correlationId": "corr-200",
                  "causationId": "cause-200",
                  "totalAmount": 10.00,
                  "currency": "USD",
                  "items": [{"sku": "SKU-1", "quantity": 1}]
                }
                """.formatted(orderId)
        );

        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", "INVENTORY_RESERVATION_REQUESTED".getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> consumerRecord.headers().add(header));
        return consumerRecord;
    }
}

