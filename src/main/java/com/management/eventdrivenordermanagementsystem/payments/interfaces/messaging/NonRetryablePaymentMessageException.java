package com.management.eventdrivenordermanagementsystem.payments.interfaces.messaging;

public class NonRetryablePaymentMessageException extends RuntimeException {

    public NonRetryablePaymentMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

