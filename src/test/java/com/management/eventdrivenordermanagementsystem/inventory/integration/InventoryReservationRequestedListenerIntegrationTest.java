package com.management.eventdrivenordermanagementsystem.inventory.integration;

import com.management.eventdrivenordermanagementsystem.inventory.interfaces.messaging.InventoryReservationRequestedListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "outbox.relay.enabled=false",
    "outbox.kafka.enabled=false",
    "spring.kafka.listener.auto-startup=false"
})
@Sql(scripts = "classpath:sql/inventory-slice-schema.sql")
class InventoryReservationRequestedListenerIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryReservationRequestedListener listener;

    @Test
    void processesInventoryReservationRequestAndEmitsReservedEvent() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update(
            """
                insert into inventory.inventory_items (sku, available_quantity, reserved_quantity, updated_at)
                values ('SKU-1', 5, 0, ?)
                """,
            Timestamp.from(Instant.parse("2026-04-01T10:00:00Z"))
        );

        listener.onMessage(reservationRequestRecord(orderId, eventId, 3, "wf-200", "corr-200", "cause-200"));

        Integer availableQuantity = jdbcTemplate.queryForObject(
            "select available_quantity from inventory.inventory_items where sku = 'SKU-1'",
            Integer.class
        );
        Integer reservedQuantity = jdbcTemplate.queryForObject(
            "select reserved_quantity from inventory.inventory_items where sku = 'SKU-1'",
            Integer.class
        );
        String reservationStatus = jdbcTemplate.queryForObject(
            "select status from inventory.inventory_reservations where order_id = ? and sku = 'SKU-1'",
            String.class,
            orderId
        );
        String eventType = jdbcTemplate.queryForObject(
            "select event_type from outbox.outbox_event where aggregate_id = ? order by occurred_at desc limit 1",
            String.class,
            orderId.toString()
        );
        String payload = jdbcTemplate.queryForObject(
            "select payload from outbox.outbox_event where aggregate_id = ? order by occurred_at desc limit 1",
            String.class,
            orderId.toString()
        );
        Long processedCount = jdbcTemplate.queryForObject(
            "select count(*) from inventory.processed_messages where consumer_name = 'inventory-reservation-requested-listener' and message_id = ?",
            Long.class,
            eventId
        );

        assertThat(availableQuantity).isEqualTo(2);
        assertThat(reservedQuantity).isEqualTo(3);
        assertThat(reservationStatus).isEqualTo("RESERVED");
        assertThat(eventType).isEqualTo("INVENTORY_RESERVED");
        assertThat(payload).contains(orderId.toString()).contains("RESERVED");
        assertThat(processedCount).isEqualTo(1L);
    }

    @Test
    void processesInsufficientStockAsRejectedReservation() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update(
            """
                insert into inventory.inventory_items (sku, available_quantity, reserved_quantity, updated_at)
                values ('SKU-1', 2, 0, ?)
                """,
            Timestamp.from(Instant.parse("2026-04-01T10:00:00Z"))
        );

        listener.onMessage(reservationRequestRecord(orderId, eventId, 4, "wf-201", "corr-201", "cause-201"));

        Integer availableQuantity = jdbcTemplate.queryForObject(
            "select available_quantity from inventory.inventory_items where sku = 'SKU-1'",
            Integer.class
        );
        Integer reservedQuantity = jdbcTemplate.queryForObject(
            "select reserved_quantity from inventory.inventory_items where sku = 'SKU-1'",
            Integer.class
        );
        String reservationStatus = jdbcTemplate.queryForObject(
            "select status from inventory.inventory_reservations where order_id = ? and sku = 'SKU-1'",
            String.class,
            orderId
        );
        String eventType = jdbcTemplate.queryForObject(
            "select event_type from outbox.outbox_event where aggregate_id = ? order by occurred_at desc limit 1",
            String.class,
            orderId.toString()
        );
        String payload = jdbcTemplate.queryForObject(
            "select payload from outbox.outbox_event where aggregate_id = ? order by occurred_at desc limit 1",
            String.class,
            orderId.toString()
        );
        Long processedCount = jdbcTemplate.queryForObject(
            "select count(*) from inventory.processed_messages where consumer_name = 'inventory-reservation-requested-listener' and message_id = ?",
            Long.class,
            eventId
        );

        assertThat(availableQuantity).isEqualTo(2);
        assertThat(reservedQuantity).isZero();
        assertThat(reservationStatus).isEqualTo("REJECTED");
        assertThat(eventType).isEqualTo("INVENTORY_RESERVATION_REJECTED");
        assertThat(payload).contains(orderId.toString()).contains("REJECTED").contains("INSUFFICIENT_STOCK");
        assertThat(processedCount).isEqualTo(1L);
    }

    private ConsumerRecord<String, String> reservationRequestRecord(
        UUID orderId,
        UUID eventId,
        int quantity,
        String workflowId,
        String correlationId,
        String causationId
    ) {
        String payload = """
            {
              "orderId": "%s",
              "workflowId": "%s",
              "correlationId": "%s",
              "causationId": "%s",
              "totalAmount": 10.00,
              "currency": "USD",
              "items": [
                {"sku": "SKU-1", "quantity": %d}
              ]
            }
            """.formatted(orderId, workflowId, correlationId, causationId, quantity);

        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("order-events", 0, 0L, orderId.toString(), payload);
        RecordHeaders headers = new RecordHeaders();
        headers.add("eventType", "INVENTORY_RESERVATION_REQUESTED".getBytes(StandardCharsets.UTF_8));
        headers.add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
        headers.add("workflowId", workflowId.getBytes(StandardCharsets.UTF_8));
        headers.add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        headers.add("causationId", causationId.getBytes(StandardCharsets.UTF_8));
        headers.forEach(header -> consumerRecord.headers().add(header));
        return consumerRecord;
    }

    @TestConfiguration
    static class KafkaTestConfiguration {

        @Bean
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, String> consumerFactory() {
            return (ConsumerFactory<String, String>) mock(ConsumerFactory.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate() {
            return (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        }
    }
}






