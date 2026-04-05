package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;

@Component
public class InventoryConsumerFailureClassifier {

    public RuntimeException classify(Exception exception) {
        if (exception instanceof RetryableInventoryMessageException retryable) {
            return retryable;
        }
        if (exception instanceof NonRetryableInventoryMessageException nonRetryable) {
            return nonRetryable;
        }

        if (exception instanceof IOException || exception instanceof IllegalArgumentException) {
            return new NonRetryableInventoryMessageException(
                "Non-retryable inventory message failure",
                exception
            );
        }

        if (exception instanceof TransientDataAccessException
            || exception instanceof CannotAcquireLockException
            || exception instanceof CannotCreateTransactionException
            || exception instanceof QueryTimeoutException) {
            return new RetryableInventoryMessageException(
                "Retryable inventory message failure",
                exception
            );
        }

        return new RetryableInventoryMessageException(
            "Retryable inventory message failure",
            exception
        );
    }
}

