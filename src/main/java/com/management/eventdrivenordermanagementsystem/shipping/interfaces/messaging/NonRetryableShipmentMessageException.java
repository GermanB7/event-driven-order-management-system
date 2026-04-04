package com.management.eventdrivenordermanagementsystem.shipping.interfaces.messaging;

public class NonRetryableShipmentMessageException extends RuntimeException {

    public NonRetryableShipmentMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

