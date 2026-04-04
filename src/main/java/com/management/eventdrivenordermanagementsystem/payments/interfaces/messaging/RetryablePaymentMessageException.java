package com.management.eventdrivenordermanagementsystem.payments.interfaces.messaging;

public class RetryablePaymentMessageException extends RuntimeException {

    public RetryablePaymentMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

