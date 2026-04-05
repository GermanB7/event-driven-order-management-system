package com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryConsumerFailureClassifierTest {

    private InventoryConsumerFailureClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new InventoryConsumerFailureClassifier();
    }

    @Test
    void ioFailureIsNonRetryable() {
        RuntimeException classified = classifier.classify(new IOException("malformed"));

        assertThat(classified).isInstanceOf(NonRetryableInventoryMessageException.class);
    }

    @Test
    void transientDataFailureIsRetryable() {
        RuntimeException classified = classifier.classify(new TransientDataAccessResourceException("db timeout"));

        assertThat(classified).isInstanceOf(RetryableInventoryMessageException.class);
    }

    @Test
    void unknownFailureDefaultsToRetryable() {
        RuntimeException classified = classifier.classify(new RuntimeException("unexpected"));

        assertThat(classified).isInstanceOf(RetryableInventoryMessageException.class);
    }
}

