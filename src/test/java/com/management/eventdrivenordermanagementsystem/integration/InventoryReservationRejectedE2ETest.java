package com.management.eventdrivenordermanagementsystem.integration;

import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservationStatus;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderStatus;
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
class InventoryReservationRejectedE2ETest extends WorkflowE2ETestSupport {

    @Test
    void insufficientInventoryRejectsReservationAndCancelsOrder() {
        UUID orderId = createOrderWithInventoryStock(1, 2);

        awaitOrderStatus(orderId, OrderStatus.CANCELLED.name());
        awaitInventoryReservationStatus(orderId, InventoryReservationStatus.REJECTED.name());

        assertOutboxPublished(orderId, EventType.ORDER_CREATED.name());
        assertOutboxPublished(orderId, EventType.INVENTORY_RESERVATION_REQUESTED.name());
        assertOutboxPublished(orderId, EventType.INVENTORY_RESERVATION_REJECTED.name());
        assertOutboxPublished(orderId, EventType.ORDER_CANCELLED.name());
    }
}
