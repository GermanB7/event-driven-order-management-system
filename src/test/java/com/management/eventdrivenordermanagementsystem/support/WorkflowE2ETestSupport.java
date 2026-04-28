package com.management.eventdrivenordermanagementsystem.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class WorkflowE2ETestSupport {

    private static final Logger log = LoggerFactory.getLogger(WorkflowE2ETestSupport.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(150);

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
        registry.add("outbox.kafka.enabled", () -> "true");
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Ensure listener containers are properly initialized and can receive messages
        log.info("kafkaListeners_startup_check applicationContextReady={}", applicationContext != null);
    }

    protected UUID createOrder() {
        return createOrderWithInventoryStock(10, 2);
    }

    protected UUID createOrderWithInventoryStock(int availableQuantity, int orderQuantity) {
        seedInventoryStock(availableQuantity);

        UUID customerId = UUID.randomUUID();
        String requestBody = """
            {
              "customerId": "%s",
              "currency": "USD",
              "items": [
                {"sku": "SKU-1", "quantity": %d, "unitPrice": 10.00}
              ]
            }
            """.formatted(customerId, orderQuantity);

        MockHttpServletResponse response;
        try {
            response = mockMvc.perform(
                    post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create order through the HTTP entry point", exception);
        }

        try {
            var body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.path("status").asText()).isEqualTo("CREATED");
            return UUID.fromString(body.path("orderId").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse create-order response", exception);
        }
    }

    protected void awaitOrderStatus(UUID orderId, String expectedStatus) {
        awaitCondition(
            () -> expectedStatus.equals(currentOrderStatus(orderId)),
            "order " + orderId + " status " + expectedStatus
        );
    }

    protected void awaitPaymentStatus(UUID orderId, String expectedStatus) {
        awaitCondition(
            () -> expectedStatus.equals(currentPaymentStatus(orderId)),
            "payment for order " + orderId + " status " + expectedStatus
        );
    }

    protected void awaitInventoryReservationStatus(UUID orderId, String expectedStatus) {
        awaitCondition(
            () -> expectedStatus.equals(currentInventoryReservationStatus(orderId)),
            "inventory reservation for order " + orderId + " status " + expectedStatus
        );
    }

    protected void awaitShipmentStatus(UUID orderId, String expectedStatus) {
        awaitCondition(
            () -> expectedStatus.equals(currentShipmentStatus(orderId)),
            "shipment for order " + orderId + " status " + expectedStatus
        );
    }

    protected void awaitOutboxWritten(UUID orderId, String eventType) {
        awaitCondition(
            () -> currentOutboxStatus(orderId, eventType) != null,
            "outbox event " + eventType + " for order " + orderId + " to be written"
        );
    }

    protected void assertOutboxPublished(UUID orderId, String eventType) {
        awaitCondition(
            () -> "PUBLISHED".equals(currentOutboxStatus(orderId, eventType)),
            "outbox event " + eventType + " for order " + orderId + " to be published"
        );
    }

    protected void seedInventoryStock() {
        seedInventoryStock(10);
    }

    protected void seedInventoryStock(int availableQuantity) {
        jdbcTemplate.update("delete from inventory.inventory_items where sku = ?", "SKU-1");
        jdbcTemplate.update(
            """
                insert into inventory.inventory_items (sku, available_quantity, reserved_quantity, updated_at)
                values (?, ?, ?, current_timestamp())
            """,
            "SKU-1",
            availableQuantity,
            0
        );
    }

    protected String currentOrderStatus(UUID orderId) {
        return queryString("select status from orders.orders where id = ?", orderId);
    }

    protected String currentPaymentStatus(UUID orderId) {
        return queryString("select status from payments.payments where order_id = ?", orderId);
    }

    protected String currentInventoryReservationStatus(UUID orderId) {
        return queryString(
            "select status from inventory.inventory_reservations where order_id = ? order by created_at desc limit 1",
            orderId
        );
    }

    protected String currentShipmentStatus(UUID orderId) {
        return queryString("select status from shipping.shipments where order_id = ?", orderId);
    }

    protected String currentOutboxStatus(UUID orderId, String eventType) {
        return queryString(
            """
                select status
                from outbox.outbox_event
                where aggregate_id = ?
                  and event_type = ?
                order by occurred_at desc
                limit 1
                """,
            orderId.toString(),
            eventType
        );
    }

    protected void awaitCondition(BooleanSupplier condition, String description) {
        Instant deadline = Instant.now().plus(DEFAULT_TIMEOUT);
        int iterations = 0;
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                log.info("e2e_test_condition_satisfied description={} iterations={}", description, iterations);
                return;
            }

            // Relay pending outbox events to Kafka so listeners can consume them
            outboxRelayService.relayPendingEvents();
            iterations++;

            try {
                Thread.sleep(DEFAULT_POLL_INTERVAL.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for " + description, exception);
            }
        }

        log.error("e2e_test_condition_timeout description={} iterations={}", description, iterations);
        assertThat(condition.getAsBoolean()).as("Timed out waiting for %s after %d relay cycles", description, iterations).isTrue();
    }

    @Autowired
    private com.management.eventdrivenordermanagementsystem.outbox.application.OutboxRelayService outboxRelayService;

    private String queryString(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, String.class, args);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }
}
