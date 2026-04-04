package com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderCancelledUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderConfirmedUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderFulfillmentRequestedUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderInventoryReservationPendingUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderPaymentPendingUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderWorkflowEventListenerTest {

    private MarkOrderInventoryReservationPendingUseCase markInventoryReservationPendingUseCase;
    private MarkOrderPaymentPendingUseCase markPaymentPendingUseCase;
    private MarkOrderConfirmedUseCase markConfirmedUseCase;
    private MarkOrderFulfillmentRequestedUseCase markFulfillmentRequestedUseCase;
    private MarkOrderCancelledUseCase markCancelledUseCase;
    private OrderWorkflowEventListener listener;

    @BeforeEach
    void setUp() {
        markInventoryReservationPendingUseCase = mock(MarkOrderInventoryReservationPendingUseCase.class);
        markPaymentPendingUseCase = mock(MarkOrderPaymentPendingUseCase.class);
        markConfirmedUseCase = mock(MarkOrderConfirmedUseCase.class);
        markFulfillmentRequestedUseCase = mock(MarkOrderFulfillmentRequestedUseCase.class);
        markCancelledUseCase = mock(MarkOrderCancelledUseCase.class);
        listener = new OrderWorkflowEventListener(
            new ObjectMapper(),
            markInventoryReservationPendingUseCase,
            markPaymentPendingUseCase,
            markConfirmedUseCase,
            markFulfillmentRequestedUseCase,
            markCancelledUseCase,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void orderConfirmedTriggersOrderConfirmationUseCase() {
        UUID orderId = UUID.randomUUID();
        listener.onMessage(consumerRecord(EventType.ORDER_CONFIRMED, orderId));

        verify(markConfirmedUseCase).execute(orderId);
    }

    @Test
    void shipmentPreparationStartedTriggersFulfillmentRequestedUseCase() {
        UUID orderId = UUID.randomUUID();
        listener.onMessage(consumerRecord(EventType.SHIPMENT_PREPARATION_STARTED, orderId));

        verify(markFulfillmentRequestedUseCase).execute(orderId);
    }

    @Test
    void orderCancelledTriggersCancellationUseCase() {
        UUID orderId = UUID.randomUUID();
        listener.onMessage(consumerRecord(EventType.ORDER_CANCELLED, orderId));

        verify(markCancelledUseCase).execute(orderId);
    }

    private ConsumerRecord<String, String> consumerRecord(EventType eventType, UUID orderId) {
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("order-events", 0, 0L, "key", """
            {"orderId": "%s", "workflowId": "wf-500"}
            """.formatted(orderId));
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", eventType.name().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> consumerRecord.headers().add(header));
        return consumerRecord;
    }
}


