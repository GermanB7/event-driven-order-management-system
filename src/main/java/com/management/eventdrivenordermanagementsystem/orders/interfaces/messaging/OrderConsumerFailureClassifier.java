package com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;

@Component
public class OrderConsumerFailureClassifier {

    public RuntimeException classify(Exception exception) {
        if (exception instanceof RetryableOrderMessageException retryable) {
            return retryable;
        }
        if (exception instanceof NonRetryableOrderMessageException nonRetryable) {
            return nonRetryable;
        }

        if (exception instanceof IOException || exception instanceof IllegalArgumentException) {
            return new NonRetryableOrderMessageException(
                "Non-retryable order message failure",
                exception
            );
        }

        if (exception instanceof TransientDataAccessException
            || exception instanceof CannotAcquireLockException
            || exception instanceof CannotCreateTransactionException
            || exception instanceof QueryTimeoutException) {
            return new RetryableOrderMessageException(
                "Retryable order message failure",
                exception
            );
        }

        return new RetryableOrderMessageException(
            "Retryable order message failure",
            exception
        );
    }
}

