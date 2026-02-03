package com.productservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.productservice.dto.request.CreateProductRequest;
import com.productservice.dto.request.UpdateInventoryRequest;
import com.productservice.dto.response.InventoryResponse;
import com.productservice.dto.response.ProductResponse;
import com.productservice.entity.Category;
import com.productservice.repository.CategoryRepository;
import com.productservice.repository.InventoryRepository;
import com.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InventoryIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
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

    private Category testCategory;
    private String testProductId;

    @BeforeEach
    void setUp() throws Exception {
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        testCategory = Category.builder()
                .categoryId(UUID.randomUUID().toString())
                .name("Test Category " + UUID.randomUUID().toString().substring(0, 8))
                .description("Test Category Description")
                .build();
        testCategory = categoryRepository.save(testCategory);

        CreateProductRequest productRequest = CreateProductRequest.builder()
                .sku("INV-TEST-SKU-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Inventory Test Product")
                .description("Product for inventory testing")
                .categoryId(testCategory.getCategoryId())
                .price(new BigDecimal("99.99"))
                .initialStockQty(100)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        ProductResponse productResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);
        testProductId = productResponse.getProductId();
    }

    @Test
    @DisplayName("Should get inventory for product successfully")
    void getInventory_Success() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/{productId}", testProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(testProductId))
                .andExpect(jsonPath("$.stockQty").value(100))
                .andExpect(jsonPath("$.reservedQty").value(0))
                .andExpect(jsonPath("$.availableQty").value(100));
    }

    @Test
    @DisplayName("Should update inventory stock successfully")
    void updateInventory_Success() throws Exception {
        UpdateInventoryRequest request = UpdateInventoryRequest.builder()
                .stockQty(200)
                .build();

        mockMvc.perform(put("/api/v1/inventory/{productId}", testProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQty").value(200))
                .andExpect(jsonPath("$.availableQty").value(200));
    }

    @Test
    @DisplayName("Should reserve stock successfully when available")
    void reserveStock_Success() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/{productId}/reserve", testProductId)
                        .param("qty", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQty").value(100))
                .andExpect(jsonPath("$.reservedQty").value(30))
                .andExpect(jsonPath("$.availableQty").value(70));

        mockMvc.perform(post("/api/v1/inventory/{productId}/reserve", testProductId)
                        .param("qty", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedQty").value(50))
                .andExpect(jsonPath("$.availableQty").value(50));
    }

    @Test
    @DisplayName("Should return 400 when reserving more than available stock")
    void reserveStock_InsufficientStock_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/{productId}/reserve", testProductId)
                        .param("qty", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should release reserved stock successfully")
    void releaseStock_Success() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/{productId}/reserve", testProductId)
                        .param("qty", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedQty").value(50));

        mockMvc.perform(post("/api/v1/inventory/{productId}/release", testProductId)
                        .param("qty", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedQty").value(30))
                .andExpect(jsonPath("$.availableQty").value(70));
    }

    @Test
    @DisplayName("Should return 400 when releasing more than reserved")
    void releaseStock_ExceedsReserved_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/{productId}/reserve", testProductId)
                        .param("qty", "30"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inventory/{productId}/release", testProductId)
                        .param("qty", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RELEASE_QUANTITY"));
    }

    @Test
    @DisplayName("Should return 404 for non-existent product inventory")
    void getInventory_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/{productId}", "non-existent-product-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("Should handle complete reserve and release cycle")
    void reserveAndRelease_FullCycle() throws Exception {
        MvcResult reserveResult = mockMvc.perform(post("/api/v1/inventory/{productId}/reserve", testProductId)
                        .param("qty", "40"))
                .andExpect(status().isOk())
                .andReturn();

        InventoryResponse afterReserve = objectMapper.readValue(
                reserveResult.getResponse().getContentAsString(), InventoryResponse.class);
        assertThat(afterReserve.getReservedQty()).isEqualTo(40);
        assertThat(afterReserve.getAvailableQty()).isEqualTo(60);

        MvcResult releaseResult = mockMvc.perform(post("/api/v1/inventory/{productId}/release", testProductId)
                        .param("qty", "40"))
                .andExpect(status().isOk())
                .andReturn();

        InventoryResponse afterRelease = objectMapper.readValue(
                releaseResult.getResponse().getContentAsString(), InventoryResponse.class);
        assertThat(afterRelease.getReservedQty()).isEqualTo(0);
        assertThat(afterRelease.getAvailableQty()).isEqualTo(100);
    }
}
