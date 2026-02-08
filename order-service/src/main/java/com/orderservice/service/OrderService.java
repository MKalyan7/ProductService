package com.orderservice.service;

import com.orderservice.client.ProductResponse;
import com.orderservice.client.ProductServiceClient;
import com.orderservice.dto.request.CreateOrderRequest;
import com.orderservice.dto.request.OrderItemRequest;
import com.orderservice.dto.response.OrderItemResponse;
import com.orderservice.dto.response.OrderResponse;
import com.orderservice.dto.response.PageResponse;
import com.orderservice.entity.Order;
import com.orderservice.entity.OrderItem;
import com.orderservice.entity.OrderStatus;
import com.orderservice.exception.ErrorCode;
import com.orderservice.exception.InvalidOrderStateException;
import com.orderservice.exception.ProductServiceException;
import com.orderservice.exception.ResourceNotFoundException;
import com.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;

    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());

        List<OrderItem> orderItems = new ArrayList<>();
        List<ReservedItem> reservedItems = new ArrayList<>();

        try {
            for (OrderItemRequest itemRequest : request.getItems()) {
                ProductResponse product = productServiceClient.getProduct(itemRequest.getProductId());

                if (!product.isActive()) {
                    throw new ProductServiceException(ErrorCode.PRODUCT_INACTIVE,
                            "Product is inactive: " + itemRequest.getProductId());
                }

                productServiceClient.reserveInventory(itemRequest.getProductId(), itemRequest.getQuantity());
                reservedItems.add(new ReservedItem(itemRequest.getProductId(), itemRequest.getQuantity()));

                BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

                OrderItem orderItem = OrderItem.builder()
                        .productId(product.getProductId())
                        .sku(product.getSku())
                        .productName(product.getName())
                        .quantity(itemRequest.getQuantity())
                        .unitPrice(product.getPrice())
                        .lineTotal(lineTotal)
                        .build();

                orderItems.add(orderItem);
            }
        } catch (Exception e) {
            releaseReservedItems(reservedItems);
            throw e;
        }

        BigDecimal orderTotal = orderItems.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .orderId(UUID.randomUUID().toString())
                .customerId(request.getCustomerId())
                .status(OrderStatus.CREATED)
                .items(orderItems)
                .orderTotal(orderTotal)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Order created: {}", savedOrder.getOrderId());

        return mapToResponse(savedOrder);
    }

    public OrderResponse getOrderById(String orderId) {
        log.info("Fetching order: {}", orderId);
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND,
                        "Order not found: " + orderId));
        return mapToResponse(order);
    }

    public PageResponse<OrderResponse> listOrdersByCustomerId(String customerId, int page, int size, String sort) {
        log.info("Listing orders for customer: {} page: {} size: {}", customerId, page, size);

        Sort sortOrder = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String field = parts[0];
            Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortOrder = Sort.by(direction, field);
        }

        Pageable pageable = PageRequest.of(page, size, sortOrder);
        Page<Order> orderPage = orderRepository.findByCustomerId(customerId, pageable);

        List<OrderResponse> content = orderPage.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return PageResponse.<OrderResponse>builder()
                .content(content)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .first(orderPage.isFirst())
                .last(orderPage.isLast())
                .build();
    }

    public OrderResponse cancelOrder(String orderId) {
        log.info("Cancelling order: {}", orderId);
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND,
                        "Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidOrderStateException(
                    "Cannot cancel order in status: " + order.getStatus());
        }

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                try {
                    productServiceClient.releaseInventory(item.getProductId(), item.getQuantity());
                } catch (Exception e) {
                    log.warn("Failed to release inventory for product {} on order {}: {}",
                            item.getProductId(), orderId, e.getMessage());
                }
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        log.info("Order cancelled: {}", orderId);

        return mapToResponse(savedOrder);
    }

    public OrderResponse confirmOrder(String orderId) {
        log.info("Confirming order: {}", orderId);
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND,
                        "Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidOrderStateException(
                    "Cannot confirm order in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CONFIRMED);
        Order savedOrder = orderRepository.save(order);
        log.info("Order confirmed: {}", orderId);

        return mapToResponse(savedOrder);
    }

    void releaseReservedItems(List<ReservedItem> reservedItems) {
        for (ReservedItem reserved : reservedItems) {
            try {
                productServiceClient.releaseInventory(reserved.productId(), reserved.quantity());
                log.info("Released {} units for product {}", reserved.quantity(), reserved.productId());
            } catch (Exception e) {
                log.error("Failed to release inventory for product {}: {}",
                        reserved.productId(), e.getMessage());
            }
        }
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> items = order.getItems() != null
                ? order.getItems().stream().map(this::mapItemToResponse).toList()
                : List.of();

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .status(order.getStatus().name())
                .items(items)
                .orderTotal(order.getOrderTotal())
                .currency(order.getCurrency())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderItemResponse mapItemToResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .productId(item.getProductId())
                .sku(item.getSku())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(item.getLineTotal())
                .build();
    }

    record ReservedItem(String productId, int quantity) {}
}
