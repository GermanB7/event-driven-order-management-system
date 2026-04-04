package com.management.eventdrivenordermanagementsystem.payments.interfaces.messaging;

import com.management.eventdrivenordermanagementsystem.payments.domain.PaymentDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConsumerFailureClassifierTest {

    private PaymentConsumerFailureClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new PaymentConsumerFailureClassifier();
    }

    @Test
    void ioFailureIsNonRetryable() {
        RuntimeException classified = classifier.classify(new IOException("malformed"));

        assertThat(classified).isInstanceOf(NonRetryablePaymentMessageException.class);
    }

    @Test
    void domainFailureIsNonRetryable() {
        RuntimeException classified = classifier.classify(new PaymentDomainException("invalid payload"));

        assertThat(classified).isInstanceOf(NonRetryablePaymentMessageException.class);
    }

    @Test
    void transientDataFailureIsRetryable() {
        RuntimeException classified = classifier.classify(new TransientDataAccessResourceException("db timeout"));

        assertThat(classified).isInstanceOf(RetryablePaymentMessageException.class);
    }
}

