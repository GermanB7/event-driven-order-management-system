package com.management.eventdrivenordermanagementsystem.shipping.interfaces.messaging;

public class RetryableShipmentMessageException extends RuntimeException {

    public RetryableShipmentMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

