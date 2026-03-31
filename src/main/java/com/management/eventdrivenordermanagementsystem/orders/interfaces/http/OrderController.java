package com.management.eventdrivenordermanagementsystem.orders.interfaces.http;

import com.management.eventdrivenordermanagementsystem.orders.application.CreateOrderUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.GetOrderByIdUseCase;
import com.management.eventdrivenordermanagementsystem.orders.application.dto.CreateOrderCommand;
import com.management.eventdrivenordermanagementsystem.orders.application.dto.CreateOrderResult;
import com.management.eventdrivenordermanagementsystem.orders.application.dto.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderByIdUseCase getOrderByIdUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase, GetOrderByIdUseCase getOrderByIdUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderByIdUseCase = getOrderByIdUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResult createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderCommand command = new CreateOrderCommand(
            request.customerId(),
            request.currency(),
            request.items().stream()
                .map(item -> new CreateOrderCommand.CreateOrderItemCommand(item.sku(), item.quantity(), item.unitPrice()))
                .toList()
        );

        return createOrderUseCase.execute(command);
    }

    @GetMapping("/{orderId}")
    public OrderView getById(@PathVariable UUID orderId) {
        return getOrderByIdUseCase.execute(orderId);
    }

    public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotBlank String currency,
        @NotEmpty List<@Valid CreateOrderItemRequest> items
    ) {
    }

    public record CreateOrderItemRequest(
        @NotBlank String sku,
        @Positive int quantity,
        @NotNull @Positive BigDecimal unitPrice
    ) {
    }
}

