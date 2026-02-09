package com.apigateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.MethodName.class)
class RouteConfigTest {

    private static MockWebServer productService;
    private static MockWebServer orderService;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void setUp() throws IOException {
        productService = new MockWebServer();
        productService.start();

        orderService = new MockWebServer();
        orderService.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        productService.shutdown();
        orderService.shutdown();
    }

    @AfterEach
    void drainRequests() throws InterruptedException {
        while (productService.takeRequest(100, TimeUnit.MILLISECONDS) != null) { }
        while (orderService.takeRequest(100, TimeUnit.MILLISECONDS) != null) { }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("product.service.base-url", () -> "http://localhost:" + productService.getPort());
        registry.add("order.service.base-url", () -> "http://localhost:" + orderService.getPort());
    }

    @Test
    void test1_shouldRouteProductsToProductService() throws InterruptedException {
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"content\":[]}"));

        webTestClient.get()
                .uri("/api/v1/products?page=0&size=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertNotNull(body));

        RecordedRequest request = productService.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/products?page=0&size=5", request.getPath());
    }

    @Test
    void test2_shouldRouteCategoryToProductService() throws InterruptedException {
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        webTestClient.get()
                .uri("/api/v1/categories")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = productService.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/categories", request.getPath());
    }

    @Test
    void test3_shouldRouteInventoryToProductService() throws InterruptedException {
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        webTestClient.get()
                .uri("/api/v1/inventory/PROD-001")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = productService.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/inventory/PROD-001", request.getPath());
    }

    @Test
    void test4_shouldRouteOrdersToOrderService() throws InterruptedException {
        orderService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"content\":[]}"));

        webTestClient.get()
                .uri("/api/v1/orders?customerId=CUST-1001&page=0&size=5")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = orderService.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/orders?customerId=CUST-1001&page=0&size=5", request.getPath());
    }

    @Test
    void test5_shouldAddCorrelationIdToResponse() {
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        webTestClient.get()
                .uri("/api/v1/products")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id");
    }

    @Test
    void test6_shouldForwardExistingCorrelationId() {
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        String existingId = "my-custom-correlation-id";

        webTestClient.get()
                .uri("/api/v1/products")
                .header("X-Correlation-Id", existingId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", existingId);
    }
}
