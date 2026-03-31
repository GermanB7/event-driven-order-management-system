package com.management.eventdrivenordermanagementsystem.orders.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Sql(scripts = "classpath:sql/order-slice-schema.sql")
class OrderApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void postOrderPersistsOrderItemsAndOutboxAndCanBeRetrieved() throws Exception {
        UUID customerId = UUID.randomUUID();
        String requestBody = """
            {
              "customerId": "%s",
              "currency": "USD",
              "items": [
                {"sku": "SKU-1", "quantity": 2, "unitPrice": 10.50},
                {"sku": "SKU-2", "quantity": 1, "unitPrice": 5.00}
              ]
            }
            """.formatted(customerId);

        String createResponse = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String orderId = JsonPath.read(createResponse, "$.orderId");

        Integer orderCount = jdbcTemplate.queryForObject(
            "select count(*) from orders.orders where id = ?",
            Integer.class,
            UUID.fromString(orderId)
        );
        Integer itemCount = jdbcTemplate.queryForObject(
            "select count(*) from orders.order_items where order_id = ?",
            Integer.class,
            UUID.fromString(orderId)
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "select count(*) from outbox.outbox_event where aggregate_id = ? and event_type = 'ORDER_CREATED'",
            Integer.class,
            orderId
        );

        assertThat(orderCount).isEqualTo(1);
        assertThat(itemCount).isEqualTo(2);
        assertThat(outboxCount).isEqualTo(1);

        mockMvc.perform(get("/api/orders/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(orderId))
            .andExpect(jsonPath("$.customerId").value(customerId.toString()))
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.items.length()").value(2));
    }
}
