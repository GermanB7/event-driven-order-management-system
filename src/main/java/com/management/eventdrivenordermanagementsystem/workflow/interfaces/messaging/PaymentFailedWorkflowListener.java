package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentFailedWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailedWorkflowListener.class);
    private static final String ORDER_AGGREGATE_TYPE = "ORDER";

    private final ObjectMapper objectMapper;
    private final OutboxEventWriter outboxEventWriter;
    private final Counter releaseRequestedCounter;

    public PaymentFailedWorkflowListener(
        ObjectMapper objectMapper,
        OutboxEventWriter outboxEventWriter,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.outboxEventWriter = outboxEventWriter;
        this.releaseRequestedCounter = meterRegistry.counter("workflow.inventory.release.requested");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "workflow-payment-failed-listener"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");
        if (!EventType.PAYMENT_FAILED.name().equals(eventType)) {
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

            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.INVENTORY_RELEASE_REQUESTED,
                orderId,
                ORDER_AGGREGATE_TYPE,
                workflowId,
                correlationId,
                upstreamEventId,
                Instant.now(),
                1,
                buildPayload(sourcePayload, workflowId, correlationId, upstreamEventId)
            );

            outboxEventWriter.write(envelope);
            releaseRequestedCounter.increment();

            log.info(
                "workflow_inventory_release_requested orderId={} workflowId={} causationId={} eventType={}",
                orderId,
                workflowId,
                upstreamEventId,
                envelope.eventType()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to process PAYMENT_FAILED", exception);
        }
    }

    private ObjectNode buildPayload(
        JsonNode paymentFailedPayload,
        String workflowId,
        String correlationId,
        String causationId
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", paymentFailedPayload.path("orderId").asText());
        payload.put("workflowId", workflowId);
        payload.put("correlationId", correlationId);
        payload.put("causationId", causationId);

        if (paymentFailedPayload.hasNonNull("reason")) {
            payload.put("reason", paymentFailedPayload.path("reason").asText());
        }
        if (paymentFailedPayload.hasNonNull("paymentId")) {
            payload.put("paymentId", paymentFailedPayload.path("paymentId").asText());
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

