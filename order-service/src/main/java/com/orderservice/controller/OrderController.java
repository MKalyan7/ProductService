package com.orderservice.controller;

import com.orderservice.dto.request.CreateOrderRequest;
import com.orderservice.dto.response.OrderResponse;
import com.orderservice.dto.response.PageResponse;
import com.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a new order",
            description = "Creates a new order, validates products, fetches prices, and reserves inventory")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID", description = "Retrieves an order by its unique order ID")
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Order ID") @PathVariable String orderId) {
        OrderResponse response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List orders by customer ID",
            description = "Lists orders for a customer with pagination and sorting")
    public ResponseEntity<PageResponse<OrderResponse>> listOrders(
            @Parameter(description = "Customer ID", required = true) @RequestParam String customerId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field,direction (e.g. createdAt,desc)") @RequestParam(required = false) String sort) {
        PageResponse<OrderResponse> response = orderService.listOrdersByCustomerId(customerId, page, size, sort);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order",
            description = "Cancels an order in CREATED status and releases reserved inventory")
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "Order ID") @PathVariable String orderId) {
        OrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "Confirm an order",
            description = "Confirms an order, transitioning it from CREATED to CONFIRMED status")
    public ResponseEntity<OrderResponse> confirmOrder(
            @Parameter(description = "Order ID") @PathVariable String orderId) {
        OrderResponse response = orderService.confirmOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
