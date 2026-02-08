package com.orderservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderservice.entity.Order;
import com.orderservice.entity.OrderStatus;
import com.orderservice.repository.OrderRepository;
import com.orderservice.repository.SeedRunRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SeedIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    static MockWebServer mockWebServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SeedRunRepository seedRunRepository;

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
        registry.add("app.seed.endpoint.enabled", () -> "true");
    }

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        seedRunRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        seedRunRepository.deleteAll();
    }

    @Test
    void seedOrders_shouldInsertCorrectCount() throws Exception {
        setupMockProductServiceForSeed();

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.count").value(500))
                .andExpect(jsonPath("$.productsFetched").value(greaterThan(0)))
                .andExpect(jsonPath("$.durationMs").isNumber());

        List<Order> orders = orderRepository.findAll();
        assertThat(orders).hasSize(500);
    }

    @Test
    void seedOrders_shouldHaveCorrectProductIds() throws Exception {
        setupMockProductServiceForSeed();

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Order> orders = orderRepository.findAll();
        assertThat(orders).allSatisfy(order -> {
            assertThat(order.getItems()).isNotEmpty();
            assertThat(order.getItems()).allSatisfy(item -> {
                assertThat(item.getProductId()).startsWith("prod-");
                assertThat(item.getUnitPrice()).isNotNull();
                assertThat(item.getLineTotal()).isNotNull();
                assertThat(item.getQuantity()).isBetween(1, 4);
            });
        });
    }

    @Test
    void seedOrders_shouldCalculateTotalsCorrectly() throws Exception {
        setupMockProductServiceForSeed();

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Order> orders = orderRepository.findAll();
        assertThat(orders).allSatisfy(order -> {
            BigDecimal expectedTotal = order.getItems().stream()
                    .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(order.getOrderTotal().compareTo(expectedTotal)).isZero();
        });
    }

    @Test
    void seedOrders_shouldHaveStatusDistribution() throws Exception {
        setupMockProductServiceForSeed();

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Order> orders = orderRepository.findAll();
        long confirmed = orders.stream().filter(o -> o.getStatus() == OrderStatus.CONFIRMED).count();
        long created = orders.stream().filter(o -> o.getStatus() == OrderStatus.CREATED).count();
        long cancelled = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();

        assertThat(confirmed).isGreaterThan(0);
        assertThat(created).isGreaterThan(0);
        assertThat(cancelled).isGreaterThan(0);
    }

    @Test
    void seedOrders_shouldBeIdempotentWithoutReset() throws Exception {
        setupMockProductServiceForSeed();

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));

        assertThat(orderRepository.count()).isEqualTo(500);
    }

    @Test
    void seedOrders_shouldResetAndReseed() throws Exception {
        setupMockProductServiceForSeed();

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        assertThat(orderRepository.count()).isEqualTo(500);

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "99")
                        .param("reset", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(orderRepository.count()).isEqualTo(500);
    }

    @Test
    void seedOrders_shouldSetDataTag() throws Exception {
        setupMockProductServiceForSeed();

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Order> orders = orderRepository.findAll();
        assertThat(orders).allSatisfy(order ->
                assertThat(order.getDataTag()).isEqualTo("seed-v1"));
    }

    @Test
    void getStatus_shouldReturnSeedStatus() throws Exception {
        setupMockProductServiceForSeed();

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/seed/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(500))
                .andExpect(jsonPath("$.seededOrders").value(500))
                .andExpect(jsonPath("$.lastSeedRun").isNotEmpty())
                .andExpect(jsonPath("$.lastSeedRun.status").value("SUCCESS"));
    }

    @Test
    void seedOrders_shouldFailWhenNoProducts() throws Exception {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().contains("/api/v1/products")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"content\":[],\"page\":0,\"size\":100,\"totalElements\":0,\"totalPages\":0,\"first\":true,\"last\":true}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "500")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message", containsString("No active products")));
    }

    @Test
    void seedOrders_shouldEnforceMinMaxCount() throws Exception {
        setupMockProductServiceForSeed();

        mockMvc.perform(post("/api/v1/admin/seed/orders")
                        .param("count", "100")
                        .param("seed", "42")
                        .param("reset", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(500));
    }

    private void setupMockProductServiceForSeed() {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/api/v1/products") && !path.contains("/api/v1/products/")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(buildProductPageJson());
                }
                if (path != null && path.contains("/reserve")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"productId\":\"prod-1\",\"stockQty\":100,\"reservedQty\":2,\"availableQty\":98}");
                }
                if (path != null && path.contains("/release")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"productId\":\"prod-1\",\"stockQty\":100,\"reservedQty\":0,\"availableQty\":100}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }

    private String buildProductPageJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"content\":[");
        for (int i = 1; i <= 10; i++) {
            if (i > 1) sb.append(",");
            sb.append(String.format(
                    "{\"productId\":\"prod-%d\",\"sku\":\"SKU-%05d\",\"name\":\"Product %d\"," +
                            "\"price\":%.2f,\"currency\":\"USD\",\"active\":true}",
                    i, i, i, 10.0 + i * 5.0));
        }
        sb.append("],\"page\":0,\"size\":100,\"totalElements\":10,\"totalPages\":1,\"first\":true,\"last\":true}");
        return sb.toString();
    }
}
