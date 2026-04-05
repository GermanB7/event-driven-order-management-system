package com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowConsumerFailureClassifierTest {

    private WorkflowConsumerFailureClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new WorkflowConsumerFailureClassifier();
    }

    @Test
    void ioFailureIsNonRetryable() {
        RuntimeException classified = classifier.classify(new IOException("malformed"));
        assertThat(classified).isInstanceOf(NonRetryableWorkflowMessageException.class);
    }

    @Test
    void transientDataFailureIsRetryable() {
        RuntimeException classified = classifier.classify(new TransientDataAccessResourceException("db timeout"));
        assertThat(classified).isInstanceOf(RetryableWorkflowMessageException.class);
    }
}

