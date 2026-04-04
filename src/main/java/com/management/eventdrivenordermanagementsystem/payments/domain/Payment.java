package com.management.eventdrivenordermanagementsystem.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
    UUID id,
    UUID orderId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {

    public static Payment createPending(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        Instant now
    ) {
        validateBase(id, orderId, amount, currency, now);
        return new Payment(id, orderId, amount, currency, PaymentStatus.PENDING, null, now, now);
    }

    public Payment markAuthorized(Instant now) {
        if (status != PaymentStatus.PENDING) {
            throw new PaymentDomainException("Payment can move to AUTHORIZED only from PENDING");
        }
        if (now == null) {
            throw new PaymentDomainException("Payment updatedAt must be present");
        }
        return new Payment(id, orderId, amount, currency, PaymentStatus.AUTHORIZED, null, createdAt, now);
    }

    public Payment markFailed(String reason, Instant now) {
        if (status != PaymentStatus.PENDING) {
            throw new PaymentDomainException("Payment can move to FAILED only from PENDING");
        }
        if (reason == null || reason.isBlank()) {
            throw new PaymentDomainException("Payment failureReason must be present");
        }
        if (now == null) {
            throw new PaymentDomainException("Payment updatedAt must be present");
        }
        return new Payment(id, orderId, amount, currency, PaymentStatus.FAILED, reason, createdAt, now);
    }

    private static void validateBase(UUID id, UUID orderId, BigDecimal amount, String currency, Instant now) {
        if (id == null) {
            throw new PaymentDomainException("Payment id must be present");
        }
        if (orderId == null) {
            throw new PaymentDomainException("Payment orderId must be present");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentDomainException("Payment amount must be > 0");
        }
        if (currency == null || currency.isBlank()) {
            throw new PaymentDomainException("Payment currency must be present");
        }
        if (now == null) {
            throw new PaymentDomainException("Payment createdAt must be present");
        }
    }
}

