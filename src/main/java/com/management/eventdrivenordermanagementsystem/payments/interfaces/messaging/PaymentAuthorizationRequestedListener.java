package com.management.eventdrivenordermanagementsystem.payments.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.payments.application.port.PaymentRepository;
import com.management.eventdrivenordermanagementsystem.payments.domain.Payment;
import com.management.eventdrivenordermanagementsystem.payments.domain.PaymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentAuthorizationRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationRequestedListener.class);
    private static final String PAYMENT_AGGREGATE_TYPE = "PAYMENT";

    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final PaymentConsumerFailureClassifier failureClassifier;
    private final Counter acceptedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public PaymentAuthorizationRequestedListener(
        ObjectMapper objectMapper,
        PaymentRepository paymentRepository,
        OutboxEventWriter outboxEventWriter,
        PaymentConsumerFailureClassifier failureClassifier,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.paymentRepository = paymentRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.failureClassifier = failureClassifier;
        this.acceptedCounter = meterRegistry.counter("payments.authorization.request.accepted");
        this.duplicateCounter = meterRegistry.counter("payments.authorization.request.duplicate");
        this.failedCounter = meterRegistry.counter("payments.authorization.request.failed");
    }

    @KafkaListener(
        topics = "${outbox.kafka.topic.order-events:order-events}",
        groupId = "payment-authorization-requested-listener",
        containerFactory = "paymentAuthorizationKafkaListenerContainerFactory"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");
        if (!EventType.PAYMENT_AUTHORIZATION_REQUESTED.name().equals(eventType)) {
            return;
        }

        String eventId = header(record, "eventId");
        String orderId = record.key();
        String workflowId = "unknown";

        try {
            JsonNode sourcePayload = objectMapper.readTree(record.value());
            orderId = sourcePayload.path("orderId").asText();
            String upstreamEventId = header(record, "eventId");
            workflowId = sourcePayload.path("workflowId").asText(null);
            String correlationId = sourcePayload.path("correlationId").asText(null);

            if (workflowId == null || workflowId.isBlank()) {
                workflowId = orderId;
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = orderId;
            }
            if (upstreamEventId == null || upstreamEventId.isBlank()) {
                upstreamEventId = orderId;
            }

            UUID orderUuid = UUID.fromString(orderId);
            Optional<Payment> existingPayment = paymentRepository.findByOrderId(orderUuid);
            if (existingPayment.isPresent()) {
                duplicateCounter.increment();
                log.info(
                    "payment_authorization_duplicate orderId={} workflowId={} eventId={} paymentStatus={}",
                    orderId,
                    workflowId,
                    eventId,
                    existingPayment.get().status()
                );
                return;
            }

            BigDecimal amount = sourcePayload.path("amount").decimalValue();
            String currency = sourcePayload.path("currency").asText();

            Instant now = Instant.now();
            Payment payment = Payment.createPending(UUID.randomUUID(), orderUuid, amount, currency, now);
            Payment processedPayment = shouldFailAuthorization(sourcePayload)
                ? payment.markFailed(
                    sourcePayload.path("failureReason").asText("PAYMENT_PROVIDER_DECLINED"),
                    now
                )
                : payment.markAuthorized(now);

            paymentRepository.save(processedPayment);

            EventType resultEventType = processedPayment.status() == PaymentStatus.AUTHORIZED
                ? EventType.PAYMENT_AUTHORIZED
                : EventType.PAYMENT_FAILED;

            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                resultEventType,
                orderId,
                PAYMENT_AGGREGATE_TYPE,
                workflowId,
                correlationId,
                upstreamEventId,
                Instant.now(),
                1,
                buildResultPayload(sourcePayload, processedPayment, workflowId, correlationId, upstreamEventId)
            );

            outboxEventWriter.write(envelope);
            acceptedCounter.increment();

            log.info(
                "payment_authorization_processed orderId={} workflowId={} eventId={} paymentId={} resultEventType={}",
                orderId,
                workflowId,
                eventId,
                processedPayment.id(),
                resultEventType
            );
        } catch (Exception exception) {
            RuntimeException classified = failureClassifier.classify(exception);
            failedCounter.increment();
            log.error(
                "payment_authorization_failed orderId={} workflowId={} eventId={} eventType={} retryable={} failureReason={}",
                orderId,
                workflowId,
                eventId,
                eventType,
                classified instanceof RetryablePaymentMessageException,
                exception.getClass().getSimpleName()
            );
            throw classified;
        }
    }

    private ObjectNode buildResultPayload(
        JsonNode sourcePayload,
        Payment payment,
        String workflowId,
        String correlationId,
        String causationId
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", sourcePayload.path("orderId").asText());
        payload.put("workflowId", workflowId);
        payload.put("correlationId", correlationId);
        payload.put("causationId", causationId);
        payload.put("paymentId", payment.id().toString());
        payload.put("paymentStatus", payment.status().name());
        payload.put("currency", payment.currency());
        payload.put("amount", payment.amount());

        if (sourcePayload.hasNonNull("reservationId")) {
            payload.put("reservationId", sourcePayload.path("reservationId").asText());
        }
        if (sourcePayload.hasNonNull("customerId")) {
            payload.put("customerId", sourcePayload.path("customerId").asText());
        }
        if (payment.failureReason() != null && !payment.failureReason().isBlank()) {
            payload.put("reason", payment.failureReason());
        }

        return payload;
    }

    private boolean shouldFailAuthorization(JsonNode sourcePayload) {
        return sourcePayload.path("forceFailure").asBoolean(false);
    }

    private String header(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}




