package com.management.eventdrivenordermanagementsystem.orders.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.eventdrivenordermanagementsystem.messaging.application.OutboxEventWriter;
import com.management.eventdrivenordermanagementsystem.orders.application.dto.CreateOrderCommand;
import com.management.eventdrivenordermanagementsystem.orders.application.dto.CreateOrderResult;
import com.management.eventdrivenordermanagementsystem.orders.application.port.OrderRepository;
import com.management.eventdrivenordermanagementsystem.orders.domain.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    private CreateOrderUseCase createOrderUseCase;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        createOrderUseCase = new CreateOrderUseCase(orderRepository, outboxEventWriter, new ObjectMapper());
    }

    @Test
    void createsOrderAndWritesOrderCreatedEventToOutbox() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
            UUID.randomUUID(),
            "USD",
            List.of(new CreateOrderCommand.CreateOrderItemCommand("SKU-1", 2, new BigDecimal("12.50")))
        );

        CreateOrderResult result = createOrderUseCase.execute(command);

        ArgumentCaptor<Order> savedOrderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).save(savedOrderCaptor.capture());
        verify(outboxEventWriter, times(1)).write(any());

        Order savedOrder = savedOrderCaptor.getValue();
        assertThat(savedOrder.status().name()).isEqualTo("CREATED");
        assertThat(result.totalAmount()).isEqualByComparingTo("25.00");
        assertThat(result.orderId()).isEqualTo(savedOrder.id());
    }
}
