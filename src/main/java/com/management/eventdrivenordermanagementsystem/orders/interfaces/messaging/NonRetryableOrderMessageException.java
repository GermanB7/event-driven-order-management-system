package com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging;

public class NonRetryableOrderMessageException extends RuntimeException {

    public NonRetryableOrderMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

