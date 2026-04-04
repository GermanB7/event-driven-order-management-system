package com.management.eventdrivenordermanagementsystem.shipping.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.shipping.application.port.ShipmentRepository;
import com.management.eventdrivenordermanagementsystem.shipping.domain.Shipment;
import com.management.eventdrivenordermanagementsystem.shipping.domain.ShipmentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class ShipmentPreparationRequestedWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentPreparationRequestedWorkflowListener.class);
    private static final String SHIPMENT_AGGREGATE_TYPE = "SHIPMENT";

    private final ObjectMapper objectMapper;
    private final ShipmentRepository shipmentRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final Counter acceptedCounter;
    private final Counter duplicateCounter;

    public ShipmentPreparationRequestedWorkflowListener(
        ObjectMapper objectMapper,
        ShipmentRepository shipmentRepository,
        OutboxEventWriter outboxEventWriter,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.shipmentRepository = shipmentRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.acceptedCounter = meterRegistry.counter("shipping.preparation.request.accepted");
        this.duplicateCounter = meterRegistry.counter("shipping.preparation.request.duplicate");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "shipping-preparation-requested-listener"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");
        if (!EventType.SHIPMENT_PREPARATION_REQUESTED.name().equals(eventType)) {
            return;
        }

        try {
            JsonNode sourcePayload = objectMapper.readTree(record.value());
            String orderId = sourcePayload.path("orderId").asText();
            String upstreamEventId = header(record, "eventId");
            String workflowId = sourcePayload.path("workflowId").asText(null);
            String correlationId = sourcePayload.path("correlationId").asText(null);
            String shippingRequestId = sourcePayload.path("shippingRequestId").asText(null);

            if (workflowId == null || workflowId.isBlank()) {
                workflowId = orderId;
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = orderId;
            }
            if (upstreamEventId == null || upstreamEventId.isBlank()) {
                upstreamEventId = orderId;
            }
            if (shippingRequestId == null || shippingRequestId.isBlank()) {
                shippingRequestId = upstreamEventId;
            }

            Optional<Shipment> existingShipment = shipmentRepository.findByOrderId(UUID.fromString(orderId));
            if (existingShipment.isPresent() && existingShipment.get().status() == ShipmentStatus.PREPARING) {
                duplicateCounter.increment();
                log.info(
                    "shipment_preparation_duplicate orderId={} workflowId={} eventType={} shipmentStatus={}",
                    orderId,
                    workflowId,
                    eventType,
                    existingShipment.get().status()
                );
                return;
            }

            Instant now = Instant.now();
            Shipment shipment = Shipment.createPending(UUID.randomUUID(), UUID.fromString(orderId), now)
                .markPreparing(now);

            try {
                shipmentRepository.save(shipment);
            } catch (DuplicateKeyException exception) {
                duplicateCounter.increment();
                log.info(
                    "shipment_preparation_duplicate orderId={} workflowId={} eventType={} error={}",
                    orderId,
                    workflowId,
                    eventType,
                    exception.getMessage()
                );
                return;
            }

            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.SHIPMENT_PREPARATION_STARTED,
                orderId,
                SHIPMENT_AGGREGATE_TYPE,
                workflowId,
                correlationId,
                upstreamEventId,
                Instant.now(),
                1,
                buildShipmentStartedPayload(sourcePayload, workflowId, correlationId, upstreamEventId, shippingRequestId, shipment)
            );

            outboxEventWriter.write(envelope);
            acceptedCounter.increment();

            log.info(
                "shipping_preparation_started orderId={} workflowId={} causationId={} shipmentId={} shipmentStatus={}",
                orderId,
                workflowId,
                upstreamEventId,
                shipment.id(),
                shipment.status()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to process SHIPMENT_PREPARATION_REQUESTED", exception);
        }
    }

    private ObjectNode buildShipmentStartedPayload(
        JsonNode sourcePayload,
        String workflowId,
        String correlationId,
        String causationId,
        String shippingRequestId,
        Shipment shipment
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", sourcePayload.path("orderId").asText());
        payload.put("workflowId", workflowId);
        payload.put("correlationId", correlationId);
        payload.put("causationId", causationId);
        payload.put("shippingRequestId", shippingRequestId);
        payload.put("shipmentId", shipment.id().toString());
        payload.put("shipmentStatus", shipment.status().name());

        if (sourcePayload.hasNonNull("customerId")) {
            payload.put("customerId", sourcePayload.path("customerId").asText());
        }
        if (sourcePayload.hasNonNull("amount")) {
            payload.set("amount", sourcePayload.get("amount"));
        }
        if (sourcePayload.hasNonNull("currency")) {
            payload.put("currency", sourcePayload.path("currency").asText());
        }
        JsonNode itemsNode = sourcePayload.path("items");
        if (itemsNode.isArray()) {
            ArrayNode items = payload.putArray("items");
            for (JsonNode item : (ArrayNode) itemsNode) {
                ObjectNode copiedItem = items.addObject();
                copiedItem.put("sku", item.path("sku").asText());
                copiedItem.put("quantity", item.path("quantity").asInt());
            }
        }

        return payload;
    }

    private String header(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}



