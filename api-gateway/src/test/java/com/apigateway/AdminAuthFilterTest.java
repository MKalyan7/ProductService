package com.apigateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthFilterTest {

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

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("product.service.base-url", () -> "http://localhost:" + productService.getPort());
        registry.add("order.service.base-url", () -> "http://localhost:" + orderService.getPort());
        registry.add("app.admin.token", () -> "test-admin-secret");
    }

    @Test
    void shouldReturn401ForAdminRouteWithoutToken() {
        webTestClient.post()
                .uri("/api/v1/admin/seed/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturn401ForAdminRouteWithWrongToken() {
        webTestClient.post()
                .uri("/api/v1/admin/seed/orders")
                .header("X-Admin-Token", "wrong-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowAdminRouteWithCorrectToken() {
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"seeded\"}"));

        webTestClient.post()
                .uri("/api/v1/admin/seed/orders")
                .header("X-Admin-Token", "test-admin-secret")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowPublicRoutesWithoutToken() {
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        webTestClient.get()
                .uri("/api/v1/products")
                .exchange()
                .expectStatus().isOk();
    }
}
