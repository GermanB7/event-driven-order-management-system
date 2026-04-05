package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

public class RetryableWorkflowMessageException extends RuntimeException {

    public RetryableWorkflowMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

