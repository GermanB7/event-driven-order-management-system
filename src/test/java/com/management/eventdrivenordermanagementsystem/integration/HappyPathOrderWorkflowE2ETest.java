package com.management.eventdrivenordermanagementsystem.integration;

import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservationStatus;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderStatus;
import com.management.eventdrivenordermanagementsystem.payments.domain.PaymentStatus;
import com.management.eventdrivenordermanagementsystem.shipping.domain.ShipmentStatus;
import com.management.eventdrivenordermanagementsystem.support.EmbeddedKafkaTestConfiguration;
import com.management.eventdrivenordermanagementsystem.support.KafkaListenerAutoStartTestConfig;
import com.management.eventdrivenordermanagementsystem.support.WorkflowE2ETestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

/**
 * First honest end-to-end happy-path workflow test.
 *
 * This test validates that a real order flows through the complete async workflow
 * using actual infrastructure:
 *
 * Entry point -> transactional outbox -> outbox relay -> real Kafka broker ->
 * Kafka listeners -> database state changes -> workflow completion.
 *
 * This test intentionally does not call listeners directly, fabricate
 * ConsumerRecord instances, bypass Kafka, or bypass the outbox relay.
 */
@ActiveProfiles("e2e-test")
@EmbeddedKafka(partitions = 3, topics = {
    "order-events",
    "order-events.payment.dlq",
    "order-events.shipping.dlq",
    "order-events.inventory.reservation.dlq",
    "order-events.inventory.release.dlq",
    "order-events.workflow.dlq",
    "order-events.orders.dlq"
})
@Import({
    EmbeddedKafkaTestConfiguration.class,
    KafkaListenerAutoStartTestConfig.class
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
    "outbox.relay.enabled=false",
    "outbox.kafka.enabled=true",
    "spring.kafka.listener.auto-startup=true",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@Sql(scripts = "classpath:sql/e2e-workflow-schema.sql")
class HappyPathOrderWorkflowE2ETest extends WorkflowE2ETestSupport {

    @Test
    void orderFlowsCompletelyThroughAsyncWorkflowChainToFulfillment() {
        UUID orderId = createOrder();

        awaitOrderStatus(orderId, OrderStatus.FULFILLMENT_REQUESTED.name());

        awaitInventoryReservationStatus(orderId, InventoryReservationStatus.RESERVED.name());
        awaitPaymentStatus(orderId, PaymentStatus.AUTHORIZED.name());
        awaitShipmentStatus(orderId, ShipmentStatus.PREPARING.name());

        assertOutboxPublished(orderId, EventType.ORDER_CREATED.name());
        assertOutboxPublished(orderId, EventType.INVENTORY_RESERVATION_REQUESTED.name());
        assertOutboxPublished(orderId, EventType.INVENTORY_RESERVED.name());
        assertOutboxPublished(orderId, EventType.PAYMENT_AUTHORIZATION_REQUESTED.name());
        assertOutboxPublished(orderId, EventType.PAYMENT_AUTHORIZED.name());
        assertOutboxPublished(orderId, EventType.ORDER_CONFIRMED.name());
        assertOutboxPublished(orderId, EventType.SHIPMENT_PREPARATION_REQUESTED.name());
        assertOutboxPublished(orderId, EventType.SHIPMENT_PREPARATION_STARTED.name());
    }
}
