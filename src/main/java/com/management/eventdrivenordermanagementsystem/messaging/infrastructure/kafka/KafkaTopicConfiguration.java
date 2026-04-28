package com.management.eventdrivenordermanagementsystem.messaging.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    NewTopic orderEventsTopic(
        @Value("${outbox.kafka.topic.order-events:order-events}") String topic,
        @Value("${kafka.topic.default-partitions:3}") int partitions
    ) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic paymentDlqTopic(
        @Value("${payment.kafka.dlq.topic:order-events.payment.dlq}") String topic,
        @Value("${kafka.topic.default-partitions:3}") int partitions
    ) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic shippingDlqTopic(
        @Value("${shipping.kafka.dlq.topic:order-events.shipping.dlq}") String topic,
        @Value("${kafka.topic.default-partitions:3}") int partitions
    ) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic inventoryReservationDlqTopic(
        @Value("${inventory.kafka.dlq.reservation-topic:order-events.inventory.reservation.dlq}") String topic,
        @Value("${kafka.topic.default-partitions:3}") int partitions
    ) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic inventoryReleaseDlqTopic(
        @Value("${inventory.kafka.dlq.release-topic:order-events.inventory.release.dlq}") String topic,
        @Value("${kafka.topic.default-partitions:3}") int partitions
    ) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic workflowDlqTopic(
        @Value("${workflow.kafka.dlq.topic:order-events.workflow.dlq}") String topic,
        @Value("${kafka.topic.default-partitions:3}") int partitions
    ) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic ordersDlqTopic(
        @Value("${orders.kafka.dlq.topic:order-events.orders.dlq}") String topic,
        @Value("${kafka.topic.default-partitions:3}") int partitions
    ) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }
}

