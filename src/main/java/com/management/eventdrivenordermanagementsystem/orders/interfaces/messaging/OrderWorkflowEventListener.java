package com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.messaging.infrastructure.persistence.JdbcProcessedMessageStore;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderCancelledUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderConfirmedUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderFulfillmentRequestedUseCase;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class OrderWorkflowEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderWorkflowEventListener.class);
    private static final String CONSUMER_NAME = "orders-workflow-listener";

    private final ObjectMapper objectMapper;
    private final MarkOrderInventoryReservationPendingUseCase markPendingUseCase;
    private final MarkOrderPaymentPendingUseCase markPaymentPendingUseCase;
    private final MarkOrderConfirmedUseCase markConfirmedUseCase;
    private final MarkOrderFulfillmentRequestedUseCase markFulfillmentRequestedUseCase;
    private final MarkOrderCancelledUseCase markCancelledUseCase;
    private final JdbcProcessedMessageStore processedMessageStore;
    private final OrderConsumerFailureClassifier failureClassifier;
    private final Counter pendingTransitionCounter;
    private final Counter paymentPendingTransitionCounter;
    private final Counter confirmedTransitionCounter;
    private final Counter fulfillmentRequestedTransitionCounter;
    private final Counter cancelledTransitionCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public OrderWorkflowEventListener(
        ObjectMapper objectMapper,
        MarkOrderInventoryReservationPendingUseCase markPendingUseCase,
        MarkOrderPaymentPendingUseCase markPaymentPendingUseCase,
        MarkOrderConfirmedUseCase markConfirmedUseCase,
        MarkOrderFulfillmentRequestedUseCase markFulfillmentRequestedUseCase,
        MarkOrderCancelledUseCase markCancelledUseCase,
        JdbcProcessedMessageStore processedMessageStore,
        OrderConsumerFailureClassifier failureClassifier,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.markPendingUseCase = markPendingUseCase;
        this.markPaymentPendingUseCase = markPaymentPendingUseCase;
        this.markConfirmedUseCase = markConfirmedUseCase;
        this.markFulfillmentRequestedUseCase = markFulfillmentRequestedUseCase;
        this.markCancelledUseCase = markCancelledUseCase;
        this.processedMessageStore = processedMessageStore;
        this.failureClassifier = failureClassifier;
        this.pendingTransitionCounter = meterRegistry.counter("orders.workflow.inventory.pending.transition");
        this.paymentPendingTransitionCounter = meterRegistry.counter("orders.workflow.payment.pending.transition");
        this.confirmedTransitionCounter = meterRegistry.counter("orders.workflow.confirmed.transition");
        this.fulfillmentRequestedTransitionCounter = meterRegistry.counter("orders.workflow.fulfillment.requested.transition");
        this.cancelledTransitionCounter = meterRegistry.counter("orders.workflow.cancelled.transition");
        this.duplicateCounter = meterRegistry.counter("orders.workflow.duplicate");
        this.failedCounter = meterRegistry.counter("orders.workflow.failed");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "orders-workflow-listener",
        containerFactory = "orderWorkflowKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> consumerRecord) {
        String eventType = header(consumerRecord, "eventType");
        if (!EventType.INVENTORY_RESERVATION_REQUESTED.name().equals(eventType)
            && !EventType.INVENTORY_RESERVED.name().equals(eventType)
            && !EventType.ORDER_CONFIRMED.name().equals(eventType)
            && !EventType.SHIPMENT_PREPARATION_STARTED.name().equals(eventType)
            && !EventType.ORDER_CANCELLED.name().equals(eventType)) {
            return;
        }

        String eventId = requiredHeader(consumerRecord, "eventId");

        try {
            UUID messageId = UUID.fromString(eventId);
            if (!processedMessageStore.markProcessed(CONSUMER_NAME, messageId, Instant.now())) {
                duplicateCounter.increment();
                log.info("orders_workflow_duplicate consumerName={} eventType={} eventId={}", CONSUMER_NAME, eventType, eventId);
                return;
            }

            JsonNode payload = objectMapper.readTree(consumerRecord.value());
            UUID orderId = UUID.fromString(payload.path("orderId").asText());
            String workflowId = payload.path("workflowId").asText(null);

            if (EventType.INVENTORY_RESERVATION_REQUESTED.name().equals(eventType)) {
                markPendingUseCase.execute(orderId);
                pendingTransitionCounter.increment();
                log.info("order_inventory_reservation_pending orderId={} workflowId={} eventType={}", orderId, workflowId, eventType);
                return;
            }

            if (EventType.INVENTORY_RESERVED.name().equals(eventType)) {
                markPaymentPendingUseCase.execute(orderId);
                paymentPendingTransitionCounter.increment();
                log.info("order_payment_pending orderId={} workflowId={} eventType={}", orderId, workflowId, eventType);
                return;
            }

            if (EventType.ORDER_CONFIRMED.name().equals(eventType)) {
                markConfirmedUseCase.execute(orderId);
                confirmedTransitionCounter.increment();
                log.info("order_confirmed orderId={} workflowId={} eventType={}", orderId, workflowId, eventType);
                return;
            }

            if (EventType.ORDER_CANCELLED.name().equals(eventType)) {
                markCancelledUseCase.execute(orderId);
                cancelledTransitionCounter.increment();
                log.info("order_cancelled orderId={} workflowId={} eventType={}", orderId, workflowId, eventType);
                return;
            }

            markFulfillmentRequestedUseCase.execute(orderId);
            fulfillmentRequestedTransitionCounter.increment();
            log.info("order_fulfillment_requested orderId={} workflowId={} eventType={}", orderId, workflowId, eventType);
        } catch (Exception exception) {
            RuntimeException classified = failureClassifier.classify(exception);
            failedCounter.increment();
            log.error(
                "orders_workflow_failed consumerName={} eventType={} eventId={} retryable={} failureReason={}",
                CONSUMER_NAME,
                eventType,
                eventId,
                classified instanceof RetryableOrderMessageException,
                exception.getClass().getSimpleName()
            );
            throw classified;
        }
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
