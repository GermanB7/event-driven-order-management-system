package com.management.eventdrivenordermanagementsystem.orders.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventEnvelope;
import com.management.eventdrivenordermanagementsystem.messaging.event.EventType;
import com.management.eventdrivenordermanagementsystem.orders.application.dto.CreateOrderCommand;
import com.management.eventdrivenordermanagementsystem.orders.application.dto.CreateOrderResult;
import com.management.eventdrivenordermanagementsystem.orders.application.port.OrderRepository;
import com.management.eventdrivenordermanagementsystem.orders.domain.Order;
import com.management.eventdrivenordermanagementsystem.orders.domain.OrderItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";

    private final OrderRepository orderRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;

    public CreateOrderUseCase(
        OrderRepository orderRepository,
        OutboxEventWriter outboxEventWriter,
        ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateOrderResult execute(CreateOrderCommand command) {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();

        List<OrderItem> items = command.items().stream()
            .map(item -> OrderItem.create(UUID.randomUUID(), orderId, item.sku(), item.quantity(), item.unitPrice()))
            .toList();

        Order order = Order.create(orderId, command.customerId(), command.currency(), items, now);
        Order savedOrder = orderRepository.save(order);

        EventEnvelope envelope = new EventEnvelope(
            UUID.randomUUID(),
            EventType.ORDER_CREATED,
            savedOrder.id().toString(),
            AGGREGATE_TYPE_ORDER,
            savedOrder.id().toString(),
            savedOrder.id().toString(),
            savedOrder.id().toString(),
            now,
            1,
            buildPayload(savedOrder)
        );

        outboxEventWriter.write(envelope);

        return new CreateOrderResult(
            savedOrder.id(),
            savedOrder.status(),
            savedOrder.currency(),
            savedOrder.totalAmount(),
            savedOrder.createdAt()
        );
    }

    private ObjectNode buildPayload(Order order) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", order.id().toString());
        payload.put("customerId", order.customerId().toString());
        payload.put("status", order.status().name());
        payload.put("currency", order.currency());
        payload.put("totalAmount", order.totalAmount());

        ArrayNode itemsNode = payload.putArray("items");
        for (OrderItem item : order.items()) {
            ObjectNode itemNode = itemsNode.addObject();
            itemNode.put("itemId", item.id().toString());
            itemNode.put("sku", item.sku());
            itemNode.put("quantity", item.quantity());
            itemNode.put("unitPrice", item.unitPrice());
            itemNode.put("lineTotal", item.lineTotal());
        }
        return payload;
    }
}

