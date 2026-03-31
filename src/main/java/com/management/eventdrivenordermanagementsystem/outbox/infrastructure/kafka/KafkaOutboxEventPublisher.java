package com.management.eventdrivenordermanagementsystem.outbox.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.outbox.application.port.OutboxEventPublisher;
import com.management.eventdrivenordermanagementsystem.outbox.domain.OutboxEventRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(value = "outbox.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaOutboxEventPublisher implements OutboxEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final Duration publishTimeout;

    public KafkaOutboxEventPublisher(
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        @Value("${outbox.kafka.topic.order-events:order-events}") String topic,
        @Value("${outbox.kafka.publish-timeout:PT5S}") Duration publishTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.publishTimeout = publishTimeout;
    }

    @Override
    public void publish(OutboxEventRecord event) {
        ProducerRecord<String, String> producerMessage = new ProducerRecord<>(topic, event.aggregateId(), event.payload());
        addHeader(producerMessage, "eventId", event.id().toString());
        addHeader(producerMessage, "aggregateId", event.aggregateId());
        addHeader(producerMessage, "aggregateType", event.aggregateType());
        addHeader(producerMessage, "eventType", event.eventType());
        copyJsonHeaders(producerMessage, event.headers());

        try {
            kafkaTemplate.send(producerMessage).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event " + event.id(), exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Failed to publish outbox event " + event.id(), exception);
        }
    }

    private void copyJsonHeaders(ProducerRecord<String, String> producerMessage, String headersJson) {
        try {
            JsonNode headersNode = objectMapper.readTree(headersJson);
            if (!headersNode.isObject()) {
                return;
            }

            Iterator<Map.Entry<String, JsonNode>> fields = headersNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String value = field.getValue().asText(null);
                addHeader(producerMessage, field.getKey(), value);
            }
        } catch (Exception ignored) {
            // Keep relay resilient even if historical header payload is malformed.
        }
    }

    private void addHeader(ProducerRecord<String, String> producerMessage, String key, String value) {
        if (value == null) {
            return;
        }
        producerMessage.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
    }
}


