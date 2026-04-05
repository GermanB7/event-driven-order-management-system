package com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging;

public class RetryableOrderMessageException extends RuntimeException {

    public RetryableOrderMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

