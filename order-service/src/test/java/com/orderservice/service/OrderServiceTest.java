package com.orderservice.service;

import com.orderservice.client.ProductResponse;
import com.orderservice.client.ProductServiceClient;
import com.orderservice.dto.request.CreateOrderRequest;
import com.orderservice.dto.request.OrderItemRequest;
import com.orderservice.dto.response.OrderResponse;
import com.orderservice.entity.Order;
import com.orderservice.entity.OrderItem;
import com.orderservice.entity.OrderStatus;
import com.orderservice.exception.InvalidOrderStateException;
import com.orderservice.exception.ProductServiceException;
import com.orderservice.exception.ResourceNotFoundException;
import com.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_shouldCalculateTotalsCorrectly() {
        String productId1 = UUID.randomUUID().toString();
        String productId2 = UUID.randomUUID().toString();

        ProductResponse product1 = ProductResponse.builder()
                .productId(productId1)
                .sku("SKU-001")
                .name("Product 1")
                .price(new BigDecimal("29.99"))
                .currency("USD")
                .active(true)
                .build();

        ProductResponse product2 = ProductResponse.builder()
                .productId(productId2)
                .sku("SKU-002")
                .name("Product 2")
                .price(new BigDecimal("49.99"))
                .currency("USD")
                .active(true)
                .build();

        when(productServiceClient.getProduct(productId1)).thenReturn(product1);
        when(productServiceClient.getProduct(productId2)).thenReturn(product2);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId("mongo-id");
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());
            return order;
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .currency("USD")
                .items(List.of(
                        OrderItemRequest.builder().productId(productId1).quantity(2).build(),
                        OrderItemRequest.builder().productId(productId2).quantity(3).build()
                ))
                .build();

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response.getOrderId());
        assertEquals("CUST-1001", response.getCustomerId());
        assertEquals("CREATED", response.getStatus());
        assertEquals(2, response.getItems().size());

        BigDecimal expectedTotal = new BigDecimal("29.99").multiply(BigDecimal.valueOf(2))
                .add(new BigDecimal("49.99").multiply(BigDecimal.valueOf(3)));
        assertEquals(0, expectedTotal.compareTo(response.getOrderTotal()));

        assertEquals(0, new BigDecimal("59.98").compareTo(response.getItems().get(0).getLineTotal()));
        assertEquals(0, new BigDecimal("149.97").compareTo(response.getItems().get(1).getLineTotal()));
    }

    @Test
    void createOrder_shouldRollbackReservationsOnFailure() {
        String productId1 = UUID.randomUUID().toString();
        String productId2 = UUID.randomUUID().toString();

        ProductResponse product1 = ProductResponse.builder()
                .productId(productId1)
                .sku("SKU-001")
                .name("Product 1")
                .price(new BigDecimal("10.00"))
                .active(true)
                .build();

        ProductResponse product2 = ProductResponse.builder()
                .productId(productId2)
                .sku("SKU-002")
                .name("Product 2")
                .price(new BigDecimal("20.00"))
                .active(true)
                .build();

        when(productServiceClient.getProduct(productId1)).thenReturn(product1);
        when(productServiceClient.getProduct(productId2)).thenReturn(product2);

        when(productServiceClient.reserveInventory(eq(productId1), eq(2)))
                .thenReturn(new com.orderservice.client.InventoryResponse());
        when(productServiceClient.reserveInventory(eq(productId2), eq(1)))
                .thenThrow(new ProductServiceException(
                        com.orderservice.exception.ErrorCode.OUT_OF_STOCK,
                        "Insufficient stock for product: " + productId2));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        OrderItemRequest.builder().productId(productId1).quantity(2).build(),
                        OrderItemRequest.builder().productId(productId2).quantity(1).build()
                ))
                .build();

        assertThrows(ProductServiceException.class, () -> orderService.createOrder(request));

        verify(productServiceClient).releaseInventory(productId1, 2);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_shouldRollbackWhenProductInactive() {
        String productId1 = UUID.randomUUID().toString();
        String productId2 = UUID.randomUUID().toString();

        ProductResponse product1 = ProductResponse.builder()
                .productId(productId1)
                .sku("SKU-001")
                .name("Product 1")
                .price(new BigDecimal("10.00"))
                .active(true)
                .build();

        ProductResponse product2 = ProductResponse.builder()
                .productId(productId2)
                .sku("SKU-002")
                .name("Product 2")
                .price(new BigDecimal("20.00"))
                .active(false)
                .build();

        when(productServiceClient.getProduct(productId1)).thenReturn(product1);
        when(productServiceClient.getProduct(productId2)).thenReturn(product2);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        OrderItemRequest.builder().productId(productId1).quantity(2).build(),
                        OrderItemRequest.builder().productId(productId2).quantity(1).build()
                ))
                .build();

        assertThrows(ProductServiceException.class, () -> orderService.createOrder(request));

        verify(productServiceClient).releaseInventory(productId1, 2);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void cancelOrder_shouldReleaseInventoryAndSetCancelled() {
        String orderId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        Order order = Order.builder()
                .id("mongo-id")
                .orderId(orderId)
                .customerId("CUST-1001")
                .status(OrderStatus.CREATED)
                .items(List.of(
                        OrderItem.builder()
                                .productId(productId)
                                .sku("SKU-001")
                                .productName("Product 1")
                                .quantity(3)
                                .unitPrice(new BigDecimal("10.00"))
                                .lineTotal(new BigDecimal("30.00"))
                                .build()
                ))
                .orderTotal(new BigDecimal("30.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId);

        assertEquals("CANCELLED", response.getStatus());
        verify(productServiceClient).releaseInventory(productId, 3);
    }

    @Test
    void cancelOrder_shouldThrowWhenOrderConfirmed() {
        String orderId = UUID.randomUUID().toString();

        Order order = Order.builder()
                .id("mongo-id")
                .orderId(orderId)
                .customerId("CUST-1001")
                .status(OrderStatus.CONFIRMED)
                .items(List.of())
                .orderTotal(BigDecimal.ZERO)
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(orderId));
        verify(productServiceClient, never()).releaseInventory(anyString(), anyInt());
    }

    @Test
    void cancelOrder_shouldThrowWhenOrderAlreadyCancelled() {
        String orderId = UUID.randomUUID().toString();

        Order order = Order.builder()
                .id("mongo-id")
                .orderId(orderId)
                .customerId("CUST-1001")
                .status(OrderStatus.CANCELLED)
                .items(List.of())
                .orderTotal(BigDecimal.ZERO)
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(orderId));
    }

    @Test
    void confirmOrder_shouldTransitionFromCreatedToConfirmed() {
        String orderId = UUID.randomUUID().toString();

        Order order = Order.builder()
                .id("mongo-id")
                .orderId(orderId)
                .customerId("CUST-1001")
                .status(OrderStatus.CREATED)
                .items(List.of())
                .orderTotal(BigDecimal.ZERO)
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.confirmOrder(orderId);

        assertEquals("CONFIRMED", response.getStatus());
    }

    @Test
    void confirmOrder_shouldThrowWhenOrderCancelled() {
        String orderId = UUID.randomUUID().toString();

        Order order = Order.builder()
                .id("mongo-id")
                .orderId(orderId)
                .customerId("CUST-1001")
                .status(OrderStatus.CANCELLED)
                .items(List.of())
                .orderTotal(BigDecimal.ZERO)
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.confirmOrder(orderId));
    }

    @Test
    void getOrderById_shouldThrowWhenNotFound() {
        String orderId = UUID.randomUUID().toString();
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(orderId));
    }

    @Test
    void getOrderById_shouldReturnOrder() {
        String orderId = UUID.randomUUID().toString();

        Order order = Order.builder()
                .id("mongo-id")
                .orderId(orderId)
                .customerId("CUST-1001")
                .status(OrderStatus.CREATED)
                .items(List.of())
                .orderTotal(BigDecimal.ZERO)
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId);

        assertEquals(orderId, response.getOrderId());
        assertEquals("CUST-1001", response.getCustomerId());
    }
}
