package com.management.eventdrivenordermanagementsystem.integration;

import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservationStatus;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderStatus;
import com.management.eventdrivenordermanagementsystem.payments.domain.PaymentStatus;
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
 * END-TO-END PRODUCTION-GRADE COMPENSATION TEST
 *
 * This test validates that the event-driven workflow correctly handles
 * payment authorization failure and executes the full compensation chain.
 *
 * Compensation Path Validation:
 * 1. Order created via real REST API (POST /api/orders)
 * 2. Happy path begins: Inventory reserved, order moves to PAYMENT_PENDING
 * 3. PaymentAuthorizationRequestedListener declines the order amount via configured authorization threshold
 * 4. PaymentAuthorizationRequestedListener recognizes failure
 * 5. Publishes PAYMENT_FAILED event
 * 6. OrderWorkflowEventListener -> emits INVENTORY_RELEASE_REQUESTED
 * 7. InventoryReleaseRequestedListener releases inventory -> INVENTORY_RELEASED
 * 8. OrderWorkflowEventListener -> emits ORDER_CANCELLED
 * 9. Final state: Order CANCELLED, Payment FAILED, Inventory RELEASED
 *
 * This test validates:
 * - Failure detection and propagation
 * - Compensating transaction chain execution
 * - State rollback across domain boundaries
 * - Outbox consistency during failure scenarios
 *
 * Uses same E2E infrastructure as happy path:
 * - EmbeddedKafka for realistic async behavior
 * - Polling-based assertions for eventual consistency
 * - Repository queries for persisted state validation
 *
 * This is real compensation logic being tested, not mocked or stubbed.
 *
 * @see WorkflowE2ETestSupport for polling and assertion helpers
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
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "payments.authorization.fail-above-amount=15.00"
})
@Sql(scripts = "classpath:sql/e2e-workflow-schema.sql")
class PaymentFailureCompensationIntegrationTest extends WorkflowE2ETestSupport {

    @Test
    void paymentFailureTriggersInventoryReleaseAndCancelsOrder() {
        UUID orderId = createOrder();

        awaitOrderStatus(orderId, OrderStatus.CANCELLED.name());
        awaitPaymentStatus(orderId, PaymentStatus.FAILED.name());
        awaitInventoryReservationStatus(orderId, InventoryReservationStatus.RELEASED.name());

        assertOutboxPublished(orderId, EventType.ORDER_CREATED.name());
        assertOutboxPublished(orderId, EventType.INVENTORY_RESERVATION_REQUESTED.name());
        assertOutboxPublished(orderId, EventType.INVENTORY_RESERVED.name());
        assertOutboxPublished(orderId, EventType.PAYMENT_AUTHORIZATION_REQUESTED.name());
        assertOutboxPublished(orderId, EventType.PAYMENT_FAILED.name());
        assertOutboxPublished(orderId, EventType.INVENTORY_RELEASE_REQUESTED.name());
        assertOutboxPublished(orderId, EventType.INVENTORY_RELEASED.name());
        assertOutboxPublished(orderId, EventType.ORDER_CANCELLED.name());
    }
}
