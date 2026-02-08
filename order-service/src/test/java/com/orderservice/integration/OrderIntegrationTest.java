package com.orderservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderservice.dto.request.CreateOrderRequest;
import com.orderservice.dto.request.OrderItemRequest;
import com.orderservice.entity.Order;
import com.orderservice.entity.OrderItem;
import com.orderservice.entity.OrderStatus;
import com.orderservice.repository.OrderRepository;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    static MockWebServer mockWebServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    private static final String PRODUCT_ID = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeAll
    static void startMockWebServer() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopMockWebServer() throws Exception {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("product.service.base-url",
                () -> "http://localhost:" + mockWebServer.getPort());
    }

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_shouldCreateOrderSuccessfully() throws Exception {
        setupMockProductServiceForSuccess();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .currency("USD")
                .items(List.of(
                        OrderItemRequest.builder().productId(PRODUCT_ID).quantity(2).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value("CUST-1001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.items[0].productName").value("Test Product"))
                .andExpect(jsonPath("$.orderTotal").isNumber())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void createOrder_shouldReturn400WhenValidationFails() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("")
                .items(List.of())
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void createOrder_shouldReturn404WhenProductNotFound() throws Exception {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setResponseCode(404)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"errorCode\":\"PRODUCT_NOT_FOUND\",\"message\":\"Product not found\"}");
            }
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        OrderItemRequest.builder().productId("nonexistent-id").quantity(1).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void createOrder_shouldReturnErrorWhenProductInactive() throws Exception {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().contains("/api/v1/products/")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"productId\":\"" + PRODUCT_ID + "\",\"sku\":\"SKU-001\"," +
                                    "\"name\":\"Inactive Product\",\"price\":10.00,\"active\":false}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        OrderItemRequest.builder().productId(PRODUCT_ID).quantity(1).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_INACTIVE"));
    }

    @Test
    void createOrder_shouldReturnErrorWhenOutOfStock() throws Exception {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().contains("/api/v1/products/")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"productId\":\"" + PRODUCT_ID + "\",\"sku\":\"SKU-001\"," +
                                    "\"name\":\"Test Product\",\"price\":29.99,\"active\":true}");
                }
                if (request.getPath() != null && request.getPath().contains("/reserve")) {
                    return new MockResponse()
                            .setResponseCode(400)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"errorCode\":\"INSUFFICIENT_STOCK\",\"message\":\"Insufficient stock\"}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        OrderItemRequest.builder().productId(PRODUCT_ID).quantity(50).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));
    }

    @Test
    void createOrder_shouldReturn502WhenProductServiceDown() throws Exception {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setResponseCode(503)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"error\":\"Service Unavailable\"}");
            }
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        OrderItemRequest.builder().productId(PRODUCT_ID).quantity(1).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_SERVICE_UNAVAILABLE"));
    }

    @Test
    void getOrderById_shouldReturnOrder() throws Exception {
        Order order = createTestOrder("CUST-1001", OrderStatus.CREATED);

        mockMvc.perform(get("/api/v1/orders/{orderId}", order.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getOrderId()))
                .andExpect(jsonPath("$.customerId").value("CUST-1001"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getOrderById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    @Test
    void listOrders_shouldReturnPagedResults() throws Exception {
        createTestOrder("CUST-2001", OrderStatus.CREATED);
        createTestOrder("CUST-2001", OrderStatus.CONFIRMED);
        createTestOrder("CUST-9999", OrderStatus.CREATED);

        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", "CUST-2001")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void cancelOrder_shouldCancelCreatedOrder() throws Exception {
        setupMockProductServiceForRelease();
        Order order = createTestOrder("CUST-1001", OrderStatus.CREATED);

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", order.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_shouldReturn409WhenOrderConfirmed() throws Exception {
        Order order = createTestOrder("CUST-1001", OrderStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", order.getOrderId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ORDER_STATE"));
    }

    @Test
    void confirmOrder_shouldConfirmCreatedOrder() throws Exception {
        Order order = createTestOrder("CUST-1001", OrderStatus.CREATED);

        mockMvc.perform(post("/api/v1/orders/{orderId}/confirm", order.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmOrder_shouldReturn409WhenOrderCancelled() throws Exception {
        Order order = createTestOrder("CUST-1001", OrderStatus.CANCELLED);

        mockMvc.perform(post("/api/v1/orders/{orderId}/confirm", order.getOrderId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ORDER_STATE"));
    }

    @Test
    void correlationId_shouldBeReturnedInResponse() throws Exception {
        Order order = createTestOrder("CUST-1001", OrderStatus.CREATED);

        mockMvc.perform(get("/api/v1/orders/{orderId}", order.getOrderId())
                        .header("X-Correlation-Id", "test-correlation-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-correlation-123"));
    }

    @Test
    void correlationId_shouldBeGeneratedWhenNotProvided() throws Exception {
        Order order = createTestOrder("CUST-1001", OrderStatus.CREATED);

        mockMvc.perform(get("/api/v1/orders/{orderId}", order.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    private Order createTestOrder(String customerId, OrderStatus status) {
        Order order = Order.builder()
                .orderId(UUID.randomUUID().toString())
                .customerId(customerId)
                .status(status)
                .items(List.of(
                        OrderItem.builder()
                                .productId(PRODUCT_ID)
                                .sku("SKU-001")
                                .productName("Test Product")
                                .quantity(2)
                                .unitPrice(new BigDecimal("29.99"))
                                .lineTotal(new BigDecimal("59.98"))
                                .build()
                ))
                .orderTotal(new BigDecimal("59.98"))
                .currency("USD")
                .build();
        return orderRepository.save(order);
    }

    private void setupMockProductServiceForSuccess() {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().contains("/api/v1/products/")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"productId\":\"" + PRODUCT_ID + "\",\"sku\":\"SKU-001\"," +
                                    "\"name\":\"Test Product\",\"price\":29.99,\"currency\":\"USD\",\"active\":true}");
                }
                if (request.getPath() != null && request.getPath().contains("/reserve")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"productId\":\"" + PRODUCT_ID + "\",\"stockQty\":100," +
                                    "\"reservedQty\":2,\"availableQty\":98}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }

    private void setupMockProductServiceForRelease() {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().contains("/release")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"productId\":\"" + PRODUCT_ID + "\",\"stockQty\":100," +
                                    "\"reservedQty\":0,\"availableQty\":100}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }
}
