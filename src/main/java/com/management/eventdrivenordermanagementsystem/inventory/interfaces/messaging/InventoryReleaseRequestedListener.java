package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.management.eventdrivenordermanagementsystem.inventory.application.port.InventoryRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class InventoryReleaseRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryReleaseRequestedListener.class);
    private static final String INVENTORY_AGGREGATE_TYPE = "INVENTORY";
    private static final String CONSUMER_NAME = "inventory-release-requested-listener";

    private final ObjectMapper objectMapper;
    private final InventoryRepository inventoryRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final InventoryConsumerFailureClassifier failureClassifier;
    private final Counter releaseRequestedCounter;
    private final Counter releaseSucceededCounter;
    private final Counter releaseDuplicateCounter;
    private final Counter releaseFailedCounter;

    public InventoryReleaseRequestedListener(
        ObjectMapper objectMapper,
        InventoryRepository inventoryRepository,
        OutboxEventWriter outboxEventWriter,
        InventoryConsumerFailureClassifier failureClassifier,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.inventoryRepository = inventoryRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.failureClassifier = failureClassifier;
        this.releaseRequestedCounter = meterRegistry.counter("inventory.release.requested");
        this.releaseSucceededCounter = meterRegistry.counter("inventory.release.succeeded");
        this.releaseDuplicateCounter = meterRegistry.counter("inventory.release.duplicate");
        this.releaseFailedCounter = meterRegistry.counter("inventory.release.failed");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "inventory-release-requested-listener",
        containerFactory = "inventoryReleaseKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");
        if (!EventType.INVENTORY_RELEASE_REQUESTED.name().equals(eventType)) {
            return;
        }

        String eventId = header(record, "eventId");
        String orderId = record.key();
        String workflowId = "unknown";

        try {
            JsonNode sourcePayload = objectMapper.readTree(record.value());
            orderId = sourcePayload.path("orderId").asText();
            workflowId = sourcePayload.path("workflowId").asText(orderId);
            String upstreamEventId = eventId;
            String correlationId = sourcePayload.path("correlationId").asText(orderId);

            if (upstreamEventId == null || upstreamEventId.isBlank()) {
                throw new IllegalStateException("Missing required header: eventId");
            }

            UUID messageId = UUID.fromString(upstreamEventId);
            if (!inventoryRepository.markMessageProcessed(CONSUMER_NAME, messageId, Instant.now())) {
                releaseDuplicateCounter.increment();
                log.info(
                    "inventory_release_duplicate consumerName={} eventType={} eventId={} orderId={} workflowId={}",
                    CONSUMER_NAME,
                    eventType,
                    eventId,
                    orderId,
                    workflowId
                );
                return;
            }

            releaseRequestedCounter.increment();

            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.INVENTORY_RELEASED,
                orderId,
                INVENTORY_AGGREGATE_TYPE,
                workflowId,
                correlationId,
                upstreamEventId,
                Instant.now(),
                1,
                buildPayload(sourcePayload, workflowId, correlationId, upstreamEventId)
            );

            outboxEventWriter.write(envelope);
            releaseSucceededCounter.increment();

            log.info(
                "inventory_released orderId={} workflowId={} causationId={} eventType={}",
                orderId,
                workflowId,
                upstreamEventId,
                envelope.eventType()
            );
        } catch (Exception exception) {
            RuntimeException classified = failureClassifier.classify(exception);
            releaseFailedCounter.increment();
            log.error(
                "inventory_release_failed consumerName={} eventType={} eventId={} orderId={} workflowId={} retryable={} failureReason={}",
                CONSUMER_NAME,
                eventType,
                eventId,
                orderId,
                workflowId,
                classified instanceof RetryableInventoryMessageException,
                exception.getClass().getSimpleName()
            );
            throw classified;
        }
    }

    private ObjectNode buildPayload(
        JsonNode releaseRequestedPayload,
        String workflowId,
        String correlationId,
        String causationId
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", releaseRequestedPayload.path("orderId").asText());
        payload.put("workflowId", workflowId);
        payload.put("correlationId", correlationId);
        payload.put("causationId", causationId);
        payload.put("inventoryStatus", "RELEASED");

        if (releaseRequestedPayload.hasNonNull("reason")) {
            payload.put("reason", releaseRequestedPayload.path("reason").asText());
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
