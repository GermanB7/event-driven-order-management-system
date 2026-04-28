package com.management.eventdrivenordermanagementsystem.integration;

import com.management.eventdrivenordermanagementsystem.support.WorkflowE2ETestSupport;
import com.management.eventdrivenordermanagementsystem.support.EmbeddedKafkaTestConfiguration;
import com.management.eventdrivenordermanagementsystem.support.KafkaListenerStartupListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.jdbc.Sql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

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
@TestExecutionListeners(
    listeners = {KafkaListenerStartupListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@Sql(scripts = "classpath:sql/e2e-workflow-schema.sql")
class KafkaListenerDiagnosticTest extends WorkflowE2ETestSupport {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerDiagnosticTest.class);

    @Test
    void diagnosticTestCreateOrderAndWaitForInventoryReservation() {
        log.info("===== DIAGNOSTIC TEST STARTING =====");
        log.info("Test: Creating order and waiting for INVENTORY_RESERVED");
        
        UUID orderId = createOrder();
        log.info("Order created with ID: {}", orderId);
        
        // Wait just 10 seconds instead of 120 to see what happens
        try {
            Thread.sleep(5000); // 5 seconds to give consumers time to process
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        String inventoryStatus = currentInventoryReservationStatus(orderId);
        String orderStatus = currentOrderStatus(orderId);
        
        log.info("===== DIAGNOSTIC TEST RESULTS =====");
        log.info("Order Status: {}", orderStatus);
        log.info("Inventory Reservation Status: {}", inventoryStatus);
        log.info("Expected Inventory Status: RESERVED");
        log.info("===== END DIAGNOSTIC TEST =====");
        
        // This test just logs; doesn't assert. We want to see what actually happened.
    }
}



