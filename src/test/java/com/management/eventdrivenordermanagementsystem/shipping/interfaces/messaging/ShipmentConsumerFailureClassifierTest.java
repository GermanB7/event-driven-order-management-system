package com.management.eventdrivenordermanagementsystem.shipping.interfaces.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentConsumerFailureClassifierTest {

    private ShipmentConsumerFailureClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ShipmentConsumerFailureClassifier();
    }

    @Test
    void ioFailureIsNonRetryable() {
        RuntimeException classified = classifier.classify(new IOException("malformed"));

        assertThat(classified).isInstanceOf(NonRetryableShipmentMessageException.class);
    }

    @Test
    void transientDataFailureIsRetryable() {
        RuntimeException classified = classifier.classify(new TransientDataAccessResourceException("db timeout"));

        assertThat(classified).isInstanceOf(RetryableShipmentMessageException.class);
    }

    @Test
    void unknownFailureDefaultsToRetryable() {
        RuntimeException classified = classifier.classify(new RuntimeException("unexpected"));

        assertThat(classified).isInstanceOf(RetryableShipmentMessageException.class);
    }
}

