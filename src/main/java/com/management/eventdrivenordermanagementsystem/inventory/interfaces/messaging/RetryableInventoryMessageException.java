package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

public class RetryableInventoryMessageException extends RuntimeException {

    public RetryableInventoryMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

