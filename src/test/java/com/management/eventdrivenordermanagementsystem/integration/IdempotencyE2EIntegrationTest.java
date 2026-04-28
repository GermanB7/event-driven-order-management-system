package com.management.eventdrivenordermanagementsystem.integration;

import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.inventory.domain.InventoryReservationStatus;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderStatus;
import com.management.eventdrivenordermanagementsystem.support.WorkflowE2ETestSupport;
import com.management.eventdrivenordermanagementsystem.support.EmbeddedKafkaTestConfiguration;
import com.management.eventdrivenordermanagementsystem.payments.domain.PaymentStatus;
import com.management.eventdrivenordermanagementsystem.shipping.domain.ShipmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * END-TO-END IDEMPOTENCY VALIDATION TEST
 * 
 * This test validates that replaying the same event multiple times
 * does NOT result in duplicate processing or corrupted state.
 * 
 * Scenario:
 * 1. Create order and let it reach FULFILLMENT_REQUESTED (happy path)
 * 2. Re-publish the ORDER_CREATED event to Kafka manually
 * 3. Verify that the workflow recognizes it's already been processed
 * 4. Verify that no duplicate outbox events are generated
 * 5. Verify final state remains consistent
 * 
 * This validates:
 * - Idempotency table (messaging.processed_messages) is working
 * - Listeners check idempotency before processing
 * - No state duplication or corruption on replay
 * - Traceability and message deduplication across the chain
 * 
 * @see WorkflowE2ETestSupport for polling and assertion helpers
 */
@EmbeddedKafka(partitions = 3, topics = {
    "order-events",
    "order-events.payment.dlq",
    "order-events.shipping.dlq",
    "order-events.inventory.reservation.dlq",
    "order-events.inventory.release.dlq",
    "order-events.workflow.dlq",
    "order-events.orders.dlq"
})
@Import(EmbeddedKafkaTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
    "outbox.relay.enabled=false",
    "outbox.kafka.enabled=true",
    "spring.kafka.listener.auto-startup=true",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@Sql(scripts = "classpath:sql/e2e-workflow-schema.sql")
class IdempotencyE2EIntegrationTest extends WorkflowE2ETestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void replayingOrderCreatedEventIsIdempotentAndDoesNotCreateDuplicates() {
        // Step 1: Create order and let it reach happy path end state
        UUID orderId = createOrder();

        awaitOrderStatus(orderId, OrderStatus.FULFILLMENT_REQUESTED.name());
        awaitInventoryReservationStatus(orderId, InventoryReservationStatus.RESERVED.name());
        awaitPaymentStatus(orderId, PaymentStatus.AUTHORIZED.name());
        awaitShipmentStatus(orderId, ShipmentStatus.PREPARING.name());

        // Verify initial state
        assertThat(currentOrderStatus(orderId)).isEqualTo(OrderStatus.FULFILLMENT_REQUESTED.name());
        Long initialInventoryReservationCount = countInventoryReservations(orderId);
        Long initialOutboxEventCount = countOutboxEventsForOrder(orderId);
        
        // Step 2: Extract ORDER_CREATED event payload and re-publish to Kafka
        String orderCreatedPayload = queryString(
            """
                select payload
                from outbox.outbox_event
                where aggregate_id = ?
                  and event_type = ?
                order by occurred_at asc
                limit 1
                """,
            orderId.toString(),
            EventType.ORDER_CREATED.name()
        );

        assertThat(orderCreatedPayload).isNotNull();

        // Step 3: Manually re-publish ORDER_CREATED to Kafka (simulating replay)
        kafkaTemplate.send("order-events", orderId.toString(), orderCreatedPayload);

        // Wait for consumer to process (if not idempotent, it would fail or duplicate)
        try {
            Thread.sleep(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for replay processing", exception);
        }

        // Step 4: Verify state is unchanged (idempotency working)
        assertThat(currentOrderStatus(orderId)).isEqualTo(OrderStatus.FULFILLMENT_REQUESTED.name());
        
        // Step 5: Verify no duplicates were created
        Long finalInventoryReservationCount = countInventoryReservations(orderId);
        Long finalOutboxEventCount = countOutboxEventsForOrder(orderId);

        assertThat(finalInventoryReservationCount)
            .as("Replayed event should not create duplicate inventory reservations")
            .isEqualTo(initialInventoryReservationCount);

        assertThat(finalOutboxEventCount)
            .as("Replayed event should not create duplicate outbox events")
            .isEqualTo(initialOutboxEventCount);
    }

    private Long countInventoryReservations(UUID orderId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from inventory.inventory_reservations where order_id = ?",
            Long.class,
            orderId
        );
    }

    private Long countOutboxEventsForOrder(UUID orderId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from outbox.outbox_event where aggregate_id = ?",
            Long.class,
            orderId.toString()
        );
    }

    private String queryString(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, String.class, args);
        } catch (Exception exception) {
            return null;
        }
    }
}


