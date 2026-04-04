package com.management.eventdrivenordermanagementsystem.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.orders.application.CreateOrderUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderInventoryReservationPendingUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.MarkOrderPaymentPendingUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.dto.CreateOrderCommand;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderStatus;
import com.management.eventdrivenordermanagementsystem.orders.interfaces.messaging.OrderWorkflowEventListener;
import com.management.eventdrivenordermanagementsystem.shipping.interfaces.messaging.ShipmentPreparationRequestedWorkflowListener;
import com.management.eventdrivenordermanagementsystem.workflow.interfaces.messaging.PaymentAuthorizedWorkflowListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "outbox.relay.enabled=false",
    "outbox.kafka.enabled=false"
})
@Sql(scripts = "classpath:sql/sprint6-fulfillment-schema.sql")
class FulfillmentWorkflowIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private MarkOrderInventoryReservationPendingUseCase markOrderInventoryReservationPendingUseCase;

    @Autowired
    private MarkOrderPaymentPendingUseCase markOrderPaymentPendingUseCase;

    @Autowired
    private PaymentAuthorizedWorkflowListener paymentAuthorizedWorkflowListener;

    @Autowired
    private OrderWorkflowEventListener orderWorkflowEventListener;

    @Autowired
    private ShipmentPreparationRequestedWorkflowListener shipmentPreparationRequestedWorkflowListener;

    @Test
    void paymentAuthorizationCompletesWithShipmentPreparationRequested() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = createOrder(customerId);

        markOrderInventoryReservationPendingUseCase.execute(orderId);
        markOrderPaymentPendingUseCase.execute(orderId);

        ConsumerRecord<String, String> paymentAuthorizedRecord = new ConsumerRecord<>(
            "order-events",
            0,
            0L,
            orderId.toString(),
            """
                {
                  "orderId": "%s",
                  "customerId": "%s",
                  "amount": 42.50,
                  "currency": "USD"
                }
                """.formatted(orderId, customerId)
        );
        RecordHeaders paymentHeaders = new RecordHeaders();
        paymentHeaders.add("eventType", EventType.PAYMENT_AUTHORIZED.name().getBytes(StandardCharsets.UTF_8));
        paymentHeaders.add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        paymentHeaders.add("workflowId", "wf-600".getBytes(StandardCharsets.UTF_8));
        paymentHeaders.add("correlationId", "corr-600".getBytes(StandardCharsets.UTF_8));
        paymentHeaders.forEach(header -> paymentAuthorizedRecord.headers().add(header));

        paymentAuthorizedWorkflowListener.onMessage(paymentAuthorizedRecord);

        Map<String, Object> orderConfirmedEvent = outboxEvent(orderId, EventType.ORDER_CONFIRMED.name());
        Map<String, Object> shipmentRequestedEvent = outboxEvent(orderId, EventType.SHIPMENT_PREPARATION_REQUESTED.name());

        assertThat(orderConfirmedEvent).isNotNull();
        assertThat(shipmentRequestedEvent).isNotNull();

        orderWorkflowEventListener.onMessage(recordFromOutbox(orderConfirmedEvent));
        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CONFIRMED.name());

        shipmentPreparationRequestedWorkflowListener.onMessage(recordFromOutbox(shipmentRequestedEvent));
        assertThat(shipmentStatus(orderId)).isEqualTo("PREPARING");
        assertThat(outboxCount(orderId, EventType.SHIPMENT_PREPARATION_STARTED.name())).isEqualTo(1);

        Map<String, Object> shipmentStartedEvent = outboxEvent(orderId, EventType.SHIPMENT_PREPARATION_STARTED.name());
        assertThat(shipmentStartedEvent).isNotNull();
        orderWorkflowEventListener.onMessage(recordFromOutbox(shipmentStartedEvent));

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.FULFILLMENT_REQUESTED.name());
    }

    private UUID createOrder(UUID customerId) {
        CreateOrderCommand command = new CreateOrderCommand(
            customerId,
            "USD",
            List.of(new CreateOrderCommand.CreateOrderItemCommand("SKU-1", 2, new BigDecimal("10.00")))
        );

        return createOrderUseCase.execute(command).orderId();
    }

    private ConsumerRecord<String, String> recordFromOutbox(Map<String, Object> row) throws Exception {
        UUID eventId = UUID.fromString(String.valueOf(row.get("id")));
        String eventType = String.valueOf(row.get("event_type"));
        String payload = String.valueOf(row.get("payload"));
        String headersJson = String.valueOf(row.get("headers"));
        JsonNode headersNode = objectMapper.readTree(headersJson);

        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("order-events", 0, 0L, String.valueOf(row.get("aggregate_id")), payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
        addHeader(headers, headersNode, "workflowId");
        addHeader(headers, headersNode, "correlationId");
        addHeader(headers, headersNode, "causationId");
        headers.forEach(header -> consumerRecord.headers().add(header));
        return consumerRecord;
    }

    private void addHeader(RecordHeaders headers, JsonNode headersNode, String key) {
        if (headersNode.hasNonNull(key)) {
            headers.add(key, headersNode.path(key).asText().getBytes(StandardCharsets.UTF_8));
        }
    }

    private Map<String, Object> outboxEvent(UUID orderId, String eventType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
                select id as id, aggregate_id as aggregate_id, event_type as event_type, payload as payload, headers as headers
                from outbox.outbox_event
                where aggregate_id = ? and event_type = ?
                order by occurred_at asc
                """,
            orderId.toString(),
            eventType
        );
        return rows.isEmpty() ? null : rows.getLast();
    }

    private long outboxCount(UUID orderId, String eventType) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from outbox.outbox_event where aggregate_id = ? and event_type = ?",
            Long.class,
            orderId.toString(),
            eventType
        );
        return count == null ? 0L : count;
    }

    private String orderStatus(UUID orderId) {
        return jdbcTemplate.queryForObject(
            "select status from orders.orders where id = ?",
            String.class,
            orderId
        );
    }

    private String shipmentStatus(UUID orderId) {
        return jdbcTemplate.queryForObject(
            "select status from shipping.shipments where order_id = ?",
            String.class,
            orderId
        );
    }
}





