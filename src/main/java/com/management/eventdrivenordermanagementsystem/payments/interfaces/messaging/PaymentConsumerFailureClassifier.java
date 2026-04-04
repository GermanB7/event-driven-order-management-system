package com.management.eventdrivenordermanagementsystem.payments.interfaces.messaging;

import com.management.eventdrivenordermanagementsystem.payments.domain.PaymentDomainException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;

@Component
public class PaymentConsumerFailureClassifier {

    public RuntimeException classify(Exception exception) {
        if (exception instanceof RetryablePaymentMessageException retryable) {
            return retryable;
        }
        if (exception instanceof NonRetryablePaymentMessageException nonRetryable) {
            return nonRetryable;
        }

        if (exception instanceof IOException
            || exception instanceof IllegalArgumentException
            || exception instanceof PaymentDomainException) {
            return new NonRetryablePaymentMessageException("Non-retryable payment message failure", exception);
        }

        if (exception instanceof TransientDataAccessException
            || exception instanceof CannotAcquireLockException
            || exception instanceof CannotCreateTransactionException
            || exception instanceof QueryTimeoutException) {
            return new RetryablePaymentMessageException("Retryable payment message failure", exception);
        }

        return new RetryablePaymentMessageException("Retryable payment message failure", exception);
    }
}

