package com.management.eventdrivenordermanagementsystem.payments.application.port;

import com.management.eventdrivenordermanagementsystem.payments.domain.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByOrderId(UUID orderId);
}

