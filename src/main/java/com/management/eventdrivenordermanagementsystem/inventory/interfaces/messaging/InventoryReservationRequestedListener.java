package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.inventory.application.ProcessInventoryReservationRequestedUseCase;
import com.management.eventdrivenordermanagementsystem.inventory.application.dto.InventoryReservationRequestedCommand;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class InventoryReservationRequestedListener {

    private final ObjectMapper objectMapper;
    private final ProcessInventoryReservationRequestedUseCase useCase;

    public InventoryReservationRequestedListener(
        ObjectMapper objectMapper,
        ProcessInventoryReservationRequestedUseCase useCase
    ) {
        this.objectMapper = objectMapper;
        this.useCase = useCase;
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "inventory-reservation-requested-listener"
    )
    public void onMessage(ConsumerRecord<String, String> consumerRecord) {
        String eventType = header(consumerRecord, "eventType");
        if (!EventType.INVENTORY_RESERVATION_REQUESTED.name().equals(eventType)) {
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(consumerRecord.value());
            UUID messageId = UUID.fromString(requiredHeader(consumerRecord, "eventId"));
            UUID orderId = UUID.fromString(payload.path("orderId").asText());
            String workflowId = textOrFallback(payload, "workflowId", orderId.toString());
            String correlationId = textOrFallback(payload, "correlationId", orderId.toString());
            String causationId = textOrFallback(payload, "causationId", requiredHeader(consumerRecord, "eventId"));

            useCase.execute(
                new InventoryReservationRequestedCommand(
                    messageId,
                    orderId,
                    workflowId,
                    correlationId,
                    causationId,
                    Instant.now(),
                    parseItems(payload.path("items"))
                )
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to process INVENTORY_RESERVATION_REQUESTED", exception);
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


