package com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConsumerFailureClassifierTest {

    private OrderConsumerFailureClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new OrderConsumerFailureClassifier();
    }

    @Test
    void ioFailureIsNonRetryable() {
        RuntimeException classified = classifier.classify(new IOException("malformed"));
        assertThat(classified).isInstanceOf(NonRetryableOrderMessageException.class);
    }

    @Test
    void transientDataFailureIsRetryable() {
        RuntimeException classified = classifier.classify(new TransientDataAccessResourceException("db timeout"));
        assertThat(classified).isInstanceOf(RetryableOrderMessageException.class);
    }
}

