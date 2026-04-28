package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.inventory.application.ProcessInventoryReservationRequestedUseCase;
import com.management.eventdrivenordermanagementsystem.inventory.application.dto.InventoryReservationRequestedCommand;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class InventoryReservationRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationRequestedListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessInventoryReservationRequestedUseCase useCase;
    private final InventoryConsumerFailureClassifier failureClassifier;
    private final Counter failedCounter;

    public InventoryReservationRequestedListener(
        ObjectMapper objectMapper,
        ProcessInventoryReservationRequestedUseCase useCase,
        InventoryConsumerFailureClassifier failureClassifier,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.useCase = useCase;
        this.failureClassifier = failureClassifier;
        this.failedCounter = meterRegistry.counter("inventory.reservation.request.failed");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "inventory-reservation-requested-listener",
        containerFactory = "inventoryReservationKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> consumerRecord) {
        String eventType = header(consumerRecord, "eventType");
        if (!EventType.INVENTORY_RESERVATION_REQUESTED.name().equals(eventType)) {
            return;
        }

        String eventId = header(consumerRecord, "eventId");
        String orderId = consumerRecord.key();

        try {
            JsonNode payload = objectMapper.readTree(consumerRecord.value());
            UUID messageId = UUID.fromString(requiredHeader(consumerRecord, "eventId"));
            UUID orderUuid = UUID.fromString(payload.path("orderId").asText());
            String workflowId = textOrFallback(payload, "workflowId", orderUuid.toString());
            String correlationId = textOrFallback(payload, "correlationId", orderUuid.toString());
            String causationId = textOrFallback(payload, "causationId", requiredHeader(consumerRecord, "eventId"));

            useCase.execute(
                new InventoryReservationRequestedCommand(
                    messageId,
                    orderUuid,
                    workflowId,
                    correlationId,
                    causationId,
                    Instant.now(),
                    payload.path("totalAmount").decimalValue(),
                    payload.path("currency").asText(),
                    parseItems(payload.path("items"))
                )
            );
        } catch (Exception exception) {
            RuntimeException classified = failureClassifier.classify(exception);
            failedCounter.increment();
            log.error(
                "inventory_reservation_failed consumerName={} eventType={} eventId={} orderId={} retryable={} failureReason={}",
                "inventory-reservation-requested-listener",
                eventType,
                eventId,
                orderId,
                classified instanceof RetryableInventoryMessageException,
                exception.getClass().getSimpleName()
            );
            throw classified;
        }
    }

    private List<InventoryReservationRequestedCommand.RequestedItem> parseItems(JsonNode itemsNode) {
        List<InventoryReservationRequestedCommand.RequestedItem> items = new ArrayList<>();
        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                items.add(
                    new InventoryReservationRequestedCommand.RequestedItem(
                        itemNode.path("sku").asText(),
                        itemNode.path("quantity").asInt()
                    )
                );
            }
        }
        return items;
    }

    private String textOrFallback(JsonNode payload, String fieldName, String fallback) {
        String value = payload.path(fieldName).asText(null);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String header(ConsumerRecord<String, String> consumerRecord, String key) {
        Header header = consumerRecord.headers().lastHeader(key);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private String requiredHeader(ConsumerRecord<String, String> consumerRecord, String key) {
        String value = header(consumerRecord, key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required header: " + key);
        }
        return value;
    }
}
