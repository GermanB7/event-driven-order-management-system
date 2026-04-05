package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

public class NonRetryableWorkflowMessageException extends RuntimeException {

    public NonRetryableWorkflowMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

