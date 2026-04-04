package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentAuthorizedWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizedWorkflowListener.class);
    private static final String ORDER_AGGREGATE_TYPE = "ORDER";

    private final ObjectMapper objectMapper;
    private final OutboxEventWriter outboxEventWriter;
    private final Counter orderConfirmedCounter;
    private final Counter shipmentRequestedCounter;

    public PaymentAuthorizedWorkflowListener(
        ObjectMapper objectMapper,
        OutboxEventWriter outboxEventWriter,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.outboxEventWriter = outboxEventWriter;
        this.orderConfirmedCounter = meterRegistry.counter("workflow.order.confirmed.requested");
        this.shipmentRequestedCounter = meterRegistry.counter("workflow.shipment.preparation.requested");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "workflow-payment-authorized-listener"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");
        if (!EventType.PAYMENT_AUTHORIZED.name().equals(eventType)) {
            return;
        }

        try {
            JsonNode sourcePayload = objectMapper.readTree(record.value());
            String orderId = sourcePayload.path("orderId").asText();
            String upstreamEventId = header(record, "eventId");
            String workflowId = header(record, "workflowId");
            String correlationId = header(record, "correlationId");

            if (workflowId == null || workflowId.isBlank()) {
                workflowId = orderId;
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = orderId;
            }
            if (upstreamEventId == null || upstreamEventId.isBlank()) {
                upstreamEventId = orderId;
            }

            Instant now = Instant.now();
            EventEnvelope orderConfirmedEvent = new EventEnvelope(
                UUID.randomUUID(),
                EventType.ORDER_CONFIRMED,
                orderId,
                ORDER_AGGREGATE_TYPE,
                workflowId,
                correlationId,
                upstreamEventId,
                now,
                1,
                buildOrderConfirmedPayload(sourcePayload, workflowId, correlationId, upstreamEventId)
            );
            outboxEventWriter.write(orderConfirmedEvent);
            orderConfirmedCounter.increment();

            EventEnvelope shipmentRequestedEvent = new EventEnvelope(
                UUID.randomUUID(),
                EventType.SHIPMENT_PREPARATION_REQUESTED,
                orderId,
                ORDER_AGGREGATE_TYPE,
                workflowId,
                correlationId,
                orderConfirmedEvent.eventId().toString(),
                now,
                1,
                buildShipmentPreparationRequestedPayload(sourcePayload, workflowId, correlationId, orderConfirmedEvent.eventId())
            );
            outboxEventWriter.write(shipmentRequestedEvent);
            shipmentRequestedCounter.increment();

            log.info(
                "workflow_payment_authorized orderId={} workflowId={} eventType={} orderConfirmedEventId={} shipmentRequestedEventId={}",
                orderId,
                workflowId,
                eventType,
                orderConfirmedEvent.eventId(),
                shipmentRequestedEvent.eventId()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to process PAYMENT_AUTHORIZED", exception);
        }
    }

    private ObjectNode buildOrderConfirmedPayload(
        JsonNode paymentAuthorizedPayload,
        String workflowId,
        String correlationId,
        String causationId
    ) {
        ObjectNode payload = buildCommonPayload(paymentAuthorizedPayload, workflowId, correlationId, causationId);
        payload.put("orderStatus", "CONFIRMED");
        return payload;
    }

    private ObjectNode buildShipmentPreparationRequestedPayload(
        JsonNode paymentAuthorizedPayload,
        String workflowId,
        String correlationId,
        UUID causationEventId
    ) {
        ObjectNode payload = buildCommonPayload(paymentAuthorizedPayload, workflowId, correlationId, causationEventId.toString());
        payload.put("shippingRequestId", UUID.randomUUID().toString());
        payload.put("shipmentStatus", "PENDING");
        return payload;
    }

    private ObjectNode buildCommonPayload(
        JsonNode paymentAuthorizedPayload,
        String workflowId,
        String correlationId,
        String causationId
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", paymentAuthorizedPayload.path("orderId").asText());
        payload.put("workflowId", workflowId);
        payload.put("correlationId", correlationId);
        payload.put("causationId", causationId);

        if (paymentAuthorizedPayload.hasNonNull("customerId")) {
            payload.put("customerId", paymentAuthorizedPayload.path("customerId").asText());
        }
        if (paymentAuthorizedPayload.hasNonNull("amount")) {
            payload.set("amount", paymentAuthorizedPayload.get("amount"));
        } else if (paymentAuthorizedPayload.hasNonNull("totalAmount")) {
            payload.set("amount", paymentAuthorizedPayload.get("totalAmount"));
        }
        if (paymentAuthorizedPayload.hasNonNull("currency")) {
            payload.put("currency", paymentAuthorizedPayload.path("currency").asText());
        }
        if (paymentAuthorizedPayload.hasNonNull("reservationId")) {
            payload.put("reservationId", paymentAuthorizedPayload.path("reservationId").asText());
        }
        JsonNode itemsNode = paymentAuthorizedPayload.path("items");
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


