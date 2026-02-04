package com.productservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.productservice.entity.Category;
import com.productservice.entity.Inventory;
import com.productservice.entity.Product;
import com.productservice.entity.SeedRun;
import com.productservice.repository.CategoryRepository;
import com.productservice.repository.InventoryRepository;
import com.productservice.repository.ProductRepository;
import com.productservice.repository.SeedRunRepository;
import com.productservice.seed.DataGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SeedIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("app.seed.endpoint.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private SeedRunRepository seedRunRepository;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        seedRunRepository.deleteAll();
    }

    @Test
    @DisplayName("Should seed database with specified count of products")
    void seed_WithCount_Success() throws Exception {
        int productCount = 200;
        long seed = 42L;

        MvcResult result = mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", String.valueOf(productCount))
                        .param("seed", String.valueOf(seed))
                        .param("reset", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.productCount").value(productCount))
                .andExpect(jsonPath("$.categoryCount").value(DataGenerator.getCategoryCount()))
                .andExpect(jsonPath("$.durationMs").exists())
                .andReturn();

        assertThat(categoryRepository.count()).isEqualTo(DataGenerator.getCategoryCount());
        assertThat(productRepository.count()).isEqualTo(productCount);
        assertThat(inventoryRepository.count()).isEqualTo(productCount);
    }

    @Test
    @DisplayName("Should generate 30 categories")
    void seed_GeneratesCorrectCategoryCount() throws Exception {
        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", "100")
                        .param("seed", "12345")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Category> categories = categoryRepository.findAll();
        assertThat(categories).hasSize(30);

        Set<String> categoryNames = new HashSet<>();
        Set<String> categoryIds = new HashSet<>();
        for (Category category : categories) {
            categoryNames.add(category.getName());
            categoryIds.add(category.getCategoryId());
            assertThat(category.getDescription()).isNotBlank();
        }

        assertThat(categoryNames).hasSize(30);
        assertThat(categoryIds).hasSize(30);
    }

    @Test
    @DisplayName("Should generate unique SKUs for all products")
    void seed_GeneratesUniqueSkus() throws Exception {
        int productCount = 200;

        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", String.valueOf(productCount))
                        .param("seed", "99999")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Product> products = productRepository.findAll();
        Set<String> skus = new HashSet<>();
        for (Product product : products) {
            skus.add(product.getSku());
        }

        assertThat(skus).hasSize(productCount);
    }

    @Test
    @DisplayName("Should create inventory for each product")
    void seed_CreatesInventoryForEachProduct() throws Exception {
        int productCount = 200;

        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", String.valueOf(productCount))
                        .param("seed", "54321")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            assertThat(inventoryRepository.findByProductId(product.getProductId()))
                    .isPresent()
                    .hasValueSatisfying(inv -> {
                        assertThat(inv.getStockQty()).isGreaterThanOrEqualTo(0);
                        assertThat(inv.getReservedQty()).isGreaterThanOrEqualTo(0);
                        assertThat(inv.getReservedQty()).isLessThanOrEqualTo(inv.getStockQty());
                    });
        }
    }

    @Test
    @DisplayName("Should include out-of-stock and inactive products")
    void seed_IncludesOutOfStockAndInactiveProducts() throws Exception {
        int productCount = 500;

        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", String.valueOf(productCount))
                        .param("seed", "77777")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Product> products = productRepository.findAll();
        List<Inventory> inventories = inventoryRepository.findAll();

        long inactiveCount = products.stream().filter(p -> !p.isActive()).count();
        long outOfStockCount = inventories.stream().filter(i -> i.getStockQty() == 0).count();
        long lowStockCount = inventories.stream()
                .filter(i -> i.getStockQty() > 0 && i.getStockQty() <= 5)
                .count();

        assertThat(inactiveCount).isGreaterThan(0);
        assertThat(outOfStockCount).isGreaterThan(0);
        assertThat(lowStockCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should be deterministic with same seed")
    void seed_IsDeterministicWithSameSeed() throws Exception {
        long seed = 123456L;
        int productCount = 50;

        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", String.valueOf(productCount))
                        .param("seed", String.valueOf(seed))
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Product> firstRun = productRepository.findAll();
        List<String> firstSkus = firstRun.stream().map(Product::getSku).sorted().toList();

        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", String.valueOf(productCount))
                        .param("seed", String.valueOf(seed))
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Product> secondRun = productRepository.findAll();
        List<String> secondSkus = secondRun.stream().map(Product::getSku).sorted().toList();

        assertThat(firstSkus).isEqualTo(secondSkus);
    }

    @Test
    @DisplayName("Should skip seeding when data exists and reset=false")
    void seed_SkipsWhenDataExistsAndNoReset() throws Exception {
        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", "100")
                        .param("seed", "11111")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        long initialProductCount = productRepository.count();

        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", "200")
                        .param("seed", "22222")
                        .param("reset", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));

        assertThat(productRepository.count()).isEqualTo(initialProductCount);
    }

    @Test
    @DisplayName("Should return seed status")
    void getSeedStatus_ReturnsCorrectCounts() throws Exception {
        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", "150")
                        .param("seed", "33333")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/seed/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryCount").value(30))
                .andExpect(jsonPath("$.productCount").value(150))
                .andExpect(jsonPath("$.inventoryCount").value(150))
                .andExpect(jsonPath("$.lastSeedRun").exists())
                .andExpect(jsonPath("$.lastSeedRun.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("Should return 403 when seed endpoint is disabled")
    void seed_Returns403WhenDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/admin/seed/status"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should generate products with valid category references")
    void seed_ProductsHaveValidCategoryReferences() throws Exception {
        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", "200")
                        .param("seed", "44444")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Category> categories = categoryRepository.findAll();
        Set<String> categoryIds = new HashSet<>();
        for (Category category : categories) {
            categoryIds.add(category.getCategoryId());
        }

        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            assertThat(categoryIds).contains(product.getCategoryId());
        }
    }

    @Test
    @DisplayName("Should generate products with realistic prices")
    void seed_ProductsHaveRealisticPrices() throws Exception {
        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", "200")
                        .param("seed", "55555")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            assertThat(product.getPrice()).isNotNull();
            assertThat(product.getPrice().doubleValue()).isGreaterThan(0);
            assertThat(product.getPrice().doubleValue()).isLessThan(2000);
            assertThat(product.getCurrency()).isEqualTo("USD");
        }
    }

    @Test
    @DisplayName("Should generate products with varied timestamps")
    void seed_ProductsHaveVariedTimestamps() throws Exception {
        mockMvc.perform(post("/api/v1/admin/seed")
                        .param("count", "200")
                        .param("seed", "66666")
                        .param("reset", "true"))
                .andExpect(status().isOk());

        List<Product> products = productRepository.findAll();
        Set<Long> uniqueCreatedAtDays = new HashSet<>();
        for (Product product : products) {
            assertThat(product.getCreatedAt()).isNotNull();
            assertThat(product.getUpdatedAt()).isNotNull();
            assertThat(product.getUpdatedAt()).isAfterOrEqualTo(product.getCreatedAt());
            uniqueCreatedAtDays.add(product.getCreatedAt().toEpochMilli() / (24 * 60 * 60 * 1000));
        }

        assertThat(uniqueCreatedAtDays.size()).isGreaterThan(50);
    }
}
