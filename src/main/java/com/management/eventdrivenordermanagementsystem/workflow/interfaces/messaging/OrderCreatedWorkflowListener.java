package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class OrderCreatedWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedWorkflowListener.class);
    private static final String ORDER_AGGREGATE_TYPE = "ORDER";
    private static final String FIELD_ORDER_ID = "orderId";
    private static final String CONSUMER_NAME = "workflow-order-created-listener";

    private final ObjectMapper objectMapper;
    private final OutboxEventWriter outboxEventWriter;
    private final WorkflowConsumerFailureClassifier failureClassifier;
    private final JdbcProcessedMessageStore processedMessageStore;
    private final Counter requestedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public OrderCreatedWorkflowListener(
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
        this.requestedCounter = meterRegistry.counter("workflow.inventory.reservation.requested");
        this.duplicateCounter = meterRegistry.counter("workflow.order.created.duplicate");
        this.failedCounter = meterRegistry.counter("workflow.order.created.failed");
    }

    private String topicName() {
        return "${outbox.kafka.topic.order-events:order-events}";
    }

    @jakarta.annotation.PostConstruct
    private void init() {
        log.info("OrderCreatedWorkflowListener initialized and ready to consume topic='{}' with consumerName={}", topicName(), CONSUMER_NAME);
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "workflow-order-created-listener",
        containerFactory = "workflowKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> consumerRecord) {
        log.debug("OrderCreatedWorkflowListener.onMessage called headers={} key={} partition={} offset={}", consumerRecord.headers(), consumerRecord.key(), consumerRecord.partition(), consumerRecord.offset());
        String eventType = header(consumerRecord, "eventType");
        if (!EventType.ORDER_CREATED.name().equals(eventType)) {
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
            String orderId = sourcePayload.path(FIELD_ORDER_ID).asText();
            String upstreamEventId = eventId;
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
                EventType.INVENTORY_RESERVATION_REQUESTED,
                orderId,
                ORDER_AGGREGATE_TYPE,
                workflowId,
                correlationId,
                upstreamEventId,
                Instant.now(),
                1,
                buildInventoryReservationPayload(sourcePayload, workflowId, correlationId, upstreamEventId)
            );

            outboxEventWriter.write(envelope);
            requestedCounter.increment();

            log.info(
                "workflow_inventory_reservation_requested orderId={} workflowId={} causationId={} eventType={}",
                orderId,
                workflowId,
                upstreamEventId,
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

    private ObjectNode buildInventoryReservationPayload(
        JsonNode orderCreatedPayload,
        String workflowId,
        String correlationId,
        String causationId
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(FIELD_ORDER_ID, orderCreatedPayload.path(FIELD_ORDER_ID).asText());
        payload.put("workflowId", workflowId);
        payload.put("correlationId", correlationId);
        payload.put("causationId", causationId);
        payload.set("totalAmount", orderCreatedPayload.path("totalAmount"));
        payload.put("currency", orderCreatedPayload.path("currency").asText());

        ArrayNode requestedItems = payload.putArray("items");
        JsonNode sourceItems = orderCreatedPayload.path("items");
        if (sourceItems.isArray()) {
            for (JsonNode sourceItem : sourceItems) {
                ObjectNode requestedItem = requestedItems.addObject();
                requestedItem.put("sku", sourceItem.path("sku").asText());
                requestedItem.put("quantity", sourceItem.path("quantity").asInt());
            }
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
