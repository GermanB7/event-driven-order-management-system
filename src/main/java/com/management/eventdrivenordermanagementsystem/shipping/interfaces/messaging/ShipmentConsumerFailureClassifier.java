package com.management.eventdrivenordermanagementsystem.shipping.interfaces.messaging;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;

@Component
public class ShipmentConsumerFailureClassifier {

    public RuntimeException classify(Exception exception) {
        if (exception instanceof RetryableShipmentMessageException retryable) {
            return retryable;
        }
        if (exception instanceof NonRetryableShipmentMessageException nonRetryable) {
            return nonRetryable;
        }

        if (exception instanceof IOException || exception instanceof IllegalArgumentException) {
            return new NonRetryableShipmentMessageException(
                "Non-retryable shipment message failure",
                exception
            );
        }

        if (exception instanceof TransientDataAccessException
            || exception instanceof CannotAcquireLockException
            || exception instanceof CannotCreateTransactionException
            || exception instanceof QueryTimeoutException) {
            return new RetryableShipmentMessageException(
                "Retryable shipment message failure",
                exception
            );
        }

        return new RetryableShipmentMessageException(
            "Retryable shipment message failure",
            exception
        );
    }
}

