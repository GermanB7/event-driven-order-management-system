package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;

@Component
public class WorkflowConsumerFailureClassifier {

    public RuntimeException classify(Exception exception) {
        if (exception instanceof RetryableWorkflowMessageException retryable) {
            return retryable;
        }
        if (exception instanceof NonRetryableWorkflowMessageException nonRetryable) {
            return nonRetryable;
        }

        if (exception instanceof IOException || exception instanceof IllegalArgumentException) {
            return new NonRetryableWorkflowMessageException(
                "Non-retryable workflow message failure",
                exception
            );
        }

        if (exception instanceof TransientDataAccessException
            || exception instanceof CannotAcquireLockException
            || exception instanceof CannotCreateTransactionException
            || exception instanceof QueryTimeoutException) {
            return new RetryableWorkflowMessageException(
                "Retryable workflow message failure",
                exception
            );
        }

        return new RetryableWorkflowMessageException(
            "Retryable workflow message failure",
            exception
        );
    }
}

