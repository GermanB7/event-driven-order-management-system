package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

public class NonRetryableInventoryMessageException extends RuntimeException {

    public NonRetryableInventoryMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

