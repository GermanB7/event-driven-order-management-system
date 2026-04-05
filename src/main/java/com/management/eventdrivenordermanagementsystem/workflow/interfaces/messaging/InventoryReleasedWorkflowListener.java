package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.messaging.infrastructure.persistence.JdbcProcessedMessageStore;
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
import java.util.UUID;

@Component
public class InventoryReleasedWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryReleasedWorkflowListener.class);
    private static final String ORDER_AGGREGATE_TYPE = "ORDER";
    private static final String CONSUMER_NAME = "workflow-inventory-released-listener";

    private final ObjectMapper objectMapper;
    private final OutboxEventWriter outboxEventWriter;
    private final WorkflowConsumerFailureClassifier failureClassifier;
    private final JdbcProcessedMessageStore processedMessageStore;
    private final Counter orderCancelledCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public InventoryReleasedWorkflowListener(
        ObjectMapper objectMapper,
        OutboxEventWriter outboxEventWriter,
        WorkflowConsumerFailureClassifier failureClassifier,
        JdbcProcessedMessageStore processedMessageStore,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.outboxEventWriter = outboxEventWriter;
        this.failureClassifier = failureClassifier;
        this.processedMessageStore = processedMessageStore;
        this.orderCancelledCounter = meterRegistry.counter("workflow.order.cancelled");
        this.duplicateCounter = meterRegistry.counter("workflow.inventory.released.duplicate");
        this.failedCounter = meterRegistry.counter("workflow.inventory.released.failed");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "workflow-inventory-released-listener",
        containerFactory = "workflowKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> consumerRecord) {
        String eventType = header(consumerRecord, "eventType");
        if (!EventType.INVENTORY_RELEASED.name().equals(eventType)) {
            return;
        }

        String eventId = requiredHeader(consumerRecord, "eventId");
        consumerRecord.headers().add("kafka_consumer", CONSUMER_NAME.getBytes(StandardCharsets.UTF_8));

        try {
            UUID messageId = UUID.fromString(eventId);
            if (!processedMessageStore.markProcessed(CONSUMER_NAME, messageId, Instant.now())) {
                duplicateCounter.increment();
                log.info("workflow_message_duplicate consumerName={} eventType={} eventId={}", CONSUMER_NAME, eventType, eventId);
                return;
            }

            JsonNode sourcePayload = objectMapper.readTree(consumerRecord.value());
            String orderId = sourcePayload.path("orderId").asText();
            String workflowId = header(consumerRecord, "workflowId");
            String correlationId = header(consumerRecord, "correlationId");

            if (workflowId == null || workflowId.isBlank()) {
                workflowId = orderId;
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = orderId;
            }

            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.ORDER_CANCELLED,
                orderId,
                ORDER_AGGREGATE_TYPE,
                workflowId,
                correlationId,
                eventId,
                Instant.now(),
                1,
                buildPayload(sourcePayload, workflowId, correlationId, eventId)
            );

            outboxEventWriter.write(envelope);
            orderCancelledCounter.increment();

            log.info(
                "workflow_order_cancelled orderId={} workflowId={} causationId={} eventType={}",
                orderId,
                workflowId,
                eventId,
                envelope.eventType()
            );
        } catch (Exception exception) {
            RuntimeException classified = failureClassifier.classify(exception);
            failedCounter.increment();
            log.error(
                "workflow_listener_failed consumerName={} eventType={} eventId={} retryable={} failureReason={}",
                CONSUMER_NAME,
                eventType,
                eventId,
                classified instanceof RetryableWorkflowMessageException,
                exception.getClass().getSimpleName()
            );
            throw classified;
        }
    }

    private ObjectNode buildPayload(
        JsonNode inventoryReleasedPayload,
        String workflowId,
        String correlationId,
        String causationId
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", inventoryReleasedPayload.path("orderId").asText());
        payload.put("workflowId", workflowId);
        payload.put("correlationId", correlationId);
        payload.put("causationId", causationId);
        payload.put("orderStatus", "CANCELLED");

        if (inventoryReleasedPayload.hasNonNull("reason")) {
            payload.put("reason", inventoryReleasedPayload.path("reason").asText());
        }

        return payload;
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
