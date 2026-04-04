package com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderInventoryReservationPendingUseCase;
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
    private final Counter pendingTransitionCounter;

    public OrderWorkflowEventListener(
        ObjectMapper objectMapper,
        MarkOrderInventoryReservationPendingUseCase markPendingUseCase,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.markPendingUseCase = markPendingUseCase;
        this.pendingTransitionCounter = meterRegistry.counter("orders.workflow.inventory.pending.transition");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "orders-workflow-listener"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");
        if (!EventType.INVENTORY_RESERVATION_REQUESTED.name().equals(eventType)) {
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(payload.path("orderId").asText());
            String workflowId = payload.path("workflowId").asText(null);

            markPendingUseCase.execute(orderId);
            pendingTransitionCounter.increment();

            log.info(
                "order_inventory_reservation_pending orderId={} workflowId={} eventType={}",
                orderId,
                workflowId,
                eventType
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to process INVENTORY_RESERVATION_REQUESTED", exception);
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

