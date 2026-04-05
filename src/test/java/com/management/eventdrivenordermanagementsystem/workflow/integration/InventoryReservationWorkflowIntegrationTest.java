package com.management.eventdrivenordermanagementsystem.workflow.integration;

import com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging.OrderWorkflowEventListener;
import com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging.InventoryReservedWorkflowListener;
import com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging.OrderCreatedWorkflowListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "outbox.relay.enabled=false",
    "outbox.kafka.enabled=false"
})
@Sql(scripts = "classpath:sql/order-slice-schema.sql")
class InventoryReservationWorkflowIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderCreatedWorkflowListener orderCreatedWorkflowListener;

    @Autowired
    private InventoryReservedWorkflowListener inventoryReservedWorkflowListener;

    @Autowired
    private OrderWorkflowEventListener orderWorkflowEventListener;

    @Test
    void orderCreatedKickoffEmitsReservationRequestAndMovesOrderToPending() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        jdbcTemplate.update(
            """
                insert into orders.orders (id, customer_id, status, currency, total_amount, created_at, updated_at)
                values (?, ?, 'CREATED', 'USD', 30.00, ?, ?)
                """,
            orderId,
            customerId,
            Timestamp.from(Instant.parse("2026-04-01T10:00:00Z")),
            Timestamp.from(Instant.parse("2026-04-01T10:00:00Z"))
        );

        jdbcTemplate.update(
            """
                insert into orders.order_items (id, order_id, sku, quantity, unit_price, line_total)
                values (?, ?, 'SKU-1', 3, 10.00, 30.00)
                """,
            UUID.randomUUID(),
            orderId
        );

        String orderCreatedPayload = """
            {
              "orderId": "%s",
              "items": [
                {"sku": "SKU-1", "quantity": 3}
              ]
            }
            """.formatted(orderId);

        ConsumerRecord<String, String> orderCreatedRecord = new ConsumerRecord<>("order-events", 0, 0L, orderId.toString(), orderCreatedPayload);
        RecordHeaders orderCreatedHeaders = new RecordHeaders();
        orderCreatedHeaders.add("eventType", "ORDER_CREATED".getBytes(StandardCharsets.UTF_8));
        orderCreatedHeaders.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        orderCreatedHeaders.add("workflowId", orderId.toString().getBytes(StandardCharsets.UTF_8));
        orderCreatedHeaders.add("correlationId", orderId.toString().getBytes(StandardCharsets.UTF_8));
        orderCreatedHeaders.forEach(header -> orderCreatedRecord.headers().add(header));

        orderCreatedWorkflowListener.onMessage(orderCreatedRecord);

        String reservationPayload = jdbcTemplate.queryForObject(
            """
                select payload
                from outbox.outbox_event
                where aggregate_id = ?
                  and event_type = 'INVENTORY_RESERVATION_REQUESTED'
                order by occurred_at desc
                limit 1
                """,
            String.class,
            orderId.toString()
        );

        assertThat(reservationPayload).contains(orderId.toString());

        ConsumerRecord<String, String> reservationRequestedRecord = new ConsumerRecord<>(
            "order-events",
            0,
            0L,
            orderId.toString(),
            reservationPayload
        );
        RecordHeaders reservationHeaders = new RecordHeaders();
        reservationHeaders.add("eventType", "INVENTORY_RESERVATION_REQUESTED".getBytes(StandardCharsets.UTF_8));
        reservationHeaders.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        reservationHeaders.forEach(header -> reservationRequestedRecord.headers().add(header));

        orderWorkflowEventListener.onMessage(reservationRequestedRecord);

        String status = jdbcTemplate.queryForObject(
            "select status from orders.orders where id = ?",
            String.class,
            orderId
        );

        assertThat(status).isEqualTo("INVENTORY_RESERVATION_PENDING");
    }

    @Test
    void inventoryReservedEmitsPaymentAuthorizationRequestedAndMovesOrderToPaymentPending() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        jdbcTemplate.update(
            """
                insert into orders.orders (id, customer_id, status, currency, total_amount, created_at, updated_at)
                values (?, ?, 'INVENTORY_RESERVATION_PENDING', 'USD', 30.00, ?, ?)
                """,
            orderId,
            customerId,
            Timestamp.from(Instant.parse("2026-04-01T11:00:00Z")),
            Timestamp.from(Instant.parse("2026-04-01T11:00:00Z"))
        );

        jdbcTemplate.update(
            """
                insert into orders.order_items (id, order_id, sku, quantity, unit_price, line_total)
                values (?, ?, 'SKU-1', 3, 10.00, 30.00)
                """,
            UUID.randomUUID(),
            orderId
        );

        String inventoryReservedPayload = """
            {
              "orderId": "%s",
              "currency": "USD",
              "totalAmount": 30.00,
              "reservationId": "res-1"
            }
            """.formatted(orderId);

        ConsumerRecord<String, String> inventoryReservedRecord = new ConsumerRecord<>(
            "order-events",
            0,
            0L,
            orderId.toString(),
            inventoryReservedPayload
        );
        RecordHeaders inventoryReservedHeaders = new RecordHeaders();
        inventoryReservedHeaders.add("eventType", "INVENTORY_RESERVED".getBytes(StandardCharsets.UTF_8));
        inventoryReservedHeaders.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        inventoryReservedHeaders.add("workflowId", orderId.toString().getBytes(StandardCharsets.UTF_8));
        inventoryReservedHeaders.add("correlationId", orderId.toString().getBytes(StandardCharsets.UTF_8));
        inventoryReservedHeaders.forEach(header -> inventoryReservedRecord.headers().add(header));

        inventoryReservedWorkflowListener.onMessage(inventoryReservedRecord);

        String paymentPayload = jdbcTemplate.queryForObject(
            """
                select payload
                from outbox.outbox_event
                where aggregate_id = ?
                  and event_type = 'PAYMENT_AUTHORIZATION_REQUESTED'
                order by occurred_at desc
                limit 1
                """,
            String.class,
            orderId.toString()
        );

        assertThat(paymentPayload).contains(orderId.toString());

        ConsumerRecord<String, String> paymentRequestedRecord = new ConsumerRecord<>(
            "order-events",
            0,
            0L,
            orderId.toString(),
            paymentPayload
        );
        RecordHeaders paymentHeaders = new RecordHeaders();
        paymentHeaders.add("eventType", "INVENTORY_RESERVED".getBytes(StandardCharsets.UTF_8));
        paymentHeaders.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        paymentHeaders.forEach(header -> paymentRequestedRecord.headers().add(header));

        orderWorkflowEventListener.onMessage(paymentRequestedRecord);

        String status = jdbcTemplate.queryForObject(
            "select status from orders.orders where id = ?",
            String.class,
            orderId
        );

        assertThat(status).isEqualTo("PAYMENT_PENDING");
    }
}

