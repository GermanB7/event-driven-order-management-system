package com.management.eventdrivenordermanagementsystem.payments.interfaces.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.payments.application.port.PaymentRepository;
import com.management.eventdrivenordermanagementsystem.payments.domain.Payment;
import com.management.eventdrivenordermanagementsystem.payments.domain.PaymentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAuthorizationRequestedListenerTest {

    private PaymentRepository paymentRepository;
    private OutboxEventWriter outboxEventWriter;
    private PaymentAuthorizationRequestedListener listener;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        outboxEventWriter = mock(OutboxEventWriter.class);
        listener = new PaymentAuthorizationRequestedListener(
            new ObjectMapper(),
            paymentRepository,
            outboxEventWriter,
            new PaymentConsumerFailureClassifier(),
            new SimpleMeterRegistry(),
            new BigDecimal("999999999999.99")
        );
    }

    @Test
    void paymentAuthorizationRequestedEmitsPaymentAuthorizedEvent() {
        UUID orderId = UUID.randomUUID();
        String payload = """
            {
              "orderId": "%s",
              "workflowId": "wf-500",
              "correlationId": "corr-500",
              "amount": 42.50,
              "currency": "USD"
            }
            """.formatted(orderId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, orderId.toString(), payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.PAYMENT_AUTHORIZATION_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onMessage(record);

        verify(paymentRepository).save(any(Payment.class));

        org.mockito.ArgumentCaptor<EventEnvelope> envelopeCaptor = org.mockito.ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(envelopeCaptor.capture());
        EventEnvelope emitted = envelopeCaptor.getValue();

        assertThat(emitted.eventType()).isEqualTo(EventType.PAYMENT_AUTHORIZED);
        assertThat(emitted.aggregateId()).isEqualTo(orderId.toString());
        assertThat(emitted.payload().path("paymentStatus").asText()).isEqualTo(PaymentStatus.AUTHORIZED.name());
    }

    @Test
    void paymentAuthorizationAboveConfiguredThresholdEmitsPaymentFailedEvent() {
        UUID orderId = UUID.randomUUID();
        listener = new PaymentAuthorizationRequestedListener(
            new ObjectMapper(),
            paymentRepository,
            outboxEventWriter,
            new PaymentConsumerFailureClassifier(),
            new SimpleMeterRegistry(),
            new BigDecimal("15.00")
        );
        String payload = """
            {
              "orderId": "%s",
              "workflowId": "wf-503",
              "correlationId": "corr-503",
              "amount": 20.00,
              "currency": "USD"
            }
            """.formatted(orderId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, orderId.toString(), payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.PAYMENT_AUTHORIZATION_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onMessage(record);

        org.mockito.ArgumentCaptor<Payment> paymentCaptor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().status()).isEqualTo(PaymentStatus.FAILED);

        org.mockito.ArgumentCaptor<EventEnvelope> envelopeCaptor = org.mockito.ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventWriter).write(envelopeCaptor.capture());
        EventEnvelope emitted = envelopeCaptor.getValue();

        assertThat(emitted.eventType()).isEqualTo(EventType.PAYMENT_FAILED);
        assertThat(emitted.payload().path("paymentStatus").asText()).isEqualTo(PaymentStatus.FAILED.name());
        assertThat(emitted.payload().path("reason").asText()).isEqualTo("PAYMENT_PROVIDER_DECLINED");
    }

    @Test
    void duplicatePaymentAuthorizationRequestIsIgnored() {
        UUID orderId = UUID.randomUUID();
        Payment existingPayment = new Payment(
            UUID.randomUUID(),
            orderId,
            new BigDecimal("42.50"),
            "USD",
            PaymentStatus.AUTHORIZED,
            null,
            Instant.parse("2026-04-01T10:00:00Z"),
            Instant.parse("2026-04-01T10:00:01Z")
        );

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existingPayment));

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "order-events",
            0,
            0L,
            orderId.toString(),
            """
                {"orderId": "%s", "workflowId": "wf-501", "correlationId": "corr-501", "amount": 10.00, "currency": "USD"}
                """.formatted(orderId)
        );
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.PAYMENT_AUTHORIZATION_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        listener.onMessage(record);

        verify(paymentRepository, never()).save(any());
        verify(outboxEventWriter, never()).write(any());
    }

    @Test
    void malformedPayloadIsClassifiedAsNonRetryable() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order-events", 0, 0L, "key", "{invalid-json");
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.PAYMENT_AUTHORIZATION_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        assertThatThrownBy(() -> listener.onMessage(record))
            .isInstanceOf(NonRetryablePaymentMessageException.class);

        verify(paymentRepository, never()).save(any());
        verify(outboxEventWriter, never()).write(any());
    }

    @Test
    void transientRepositoryFailureIsClassifiedAsRetryable() {
        UUID orderId = UUID.randomUUID();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "order-events",
            0,
            0L,
            orderId.toString(),
            """
                {"orderId": "%s", "workflowId": "wf-502", "correlationId": "corr-502", "amount": 10.00, "currency": "USD"}
                """.formatted(orderId)
        );
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", EventType.PAYMENT_AUTHORIZATION_REQUESTED.name().getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> record.headers().add(header));

        when(paymentRepository.findByOrderId(orderId))
            .thenThrow(new TransientDataAccessResourceException("database unavailable"));

        assertThatThrownBy(() -> listener.onMessage(record))
            .isInstanceOf(RetryablePaymentMessageException.class);

        verify(outboxEventWriter, never()).write(any());
    }
}

