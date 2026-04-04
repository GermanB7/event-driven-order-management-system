package com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderInventoryReservationPendingUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderPaymentPendingUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class OrderWorkflowEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderWorkflowEventListener.class);

    private final ObjectMapper objectMapper;
    private final MarkOrderInventoryReservationPendingUseCase markPendingUseCase;
    private final MarkOrderPaymentPendingUseCase markPaymentPendingUseCase;
    private final Counter pendingTransitionCounter;
    private final Counter paymentPendingTransitionCounter;

    public OrderWorkflowEventListener(
        ObjectMapper objectMapper,
        MarkOrderInventoryReservationPendingUseCase markPendingUseCase,
        MarkOrderPaymentPendingUseCase markPaymentPendingUseCase,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.markPendingUseCase = markPendingUseCase;
        this.markPaymentPendingUseCase = markPaymentPendingUseCase;
        this.pendingTransitionCounter = meterRegistry.counter("orders.workflow.inventory.pending.transition");
        this.paymentPendingTransitionCounter = meterRegistry.counter("orders.workflow.payment.pending.transition");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "orders-workflow-listener"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");
        if (!EventType.INVENTORY_RESERVATION_REQUESTED.name().equals(eventType)
            && !EventType.INVENTORY_RESERVED.name().equals(eventType)) {
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(payload.path("orderId").asText());
            String workflowId = payload.path("workflowId").asText(null);

            if (EventType.INVENTORY_RESERVATION_REQUESTED.name().equals(eventType)) {
                markPendingUseCase.execute(orderId);
                pendingTransitionCounter.increment();

                log.info(
                    "order_inventory_reservation_pending orderId={} workflowId={} eventType={}",
                    orderId,
                    workflowId,
                    eventType
                );
                return;
            }

            markPaymentPendingUseCase.execute(orderId);
            paymentPendingTransitionCounter.increment();

            log.info(
                "order_payment_pending orderId={} workflowId={} eventType={}",
                orderId,
                workflowId,
                eventType
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to process workflow event", exception);
        }
    }

    private String header(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}

