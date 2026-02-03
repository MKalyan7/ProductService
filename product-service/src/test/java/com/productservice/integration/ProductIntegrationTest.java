package com.productservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.productservice.dto.request.CreateProductRequest;
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
class ProductIntegrationTest {

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

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        testCategory = Category.builder()
                .categoryId(UUID.randomUUID().toString())
                .name("Test Category")
                .description("Test Category Description")
                .build();
        testCategory = categoryRepository.save(testCategory);
    }

    @Test
    @DisplayName("Should create product and inventory record successfully")
    void createProduct_Success() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("INT-TEST-SKU-001")
                .name("Integration Test Product")
                .description("Product for integration testing")
                .categoryId(testCategory.getCategoryId())
                .price(new BigDecimal("49.99"))
                .currency("USD")
                .active(true)
                .initialStockQty(50)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("INT-TEST-SKU-001"))
                .andExpect(jsonPath("$.name").value("Integration Test Product"))
                .andExpect(jsonPath("$.price").value(49.99))
                .andExpect(jsonPath("$.productId").exists())
                .andReturn();

        ProductResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);

        assertThat(productRepository.findByProductId(response.getProductId())).isPresent();
        assertThat(inventoryRepository.findByProductId(response.getProductId())).isPresent();
        assertThat(inventoryRepository.findByProductId(response.getProductId()).get().getStockQty()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should return 409 CONFLICT when SKU already exists")
    void createProduct_DuplicateSku_ReturnsConflict() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("DUPLICATE-SKU")
                .name("First Product")
                .description("First product description")
                .categoryId(testCategory.getCategoryId())
                .price(new BigDecimal("29.99"))
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CreateProductRequest duplicateRequest = CreateProductRequest.builder()
                .sku("DUPLICATE-SKU")
                .name("Second Product")
                .description("Second product description")
                .categoryId(testCategory.getCategoryId())
                .price(new BigDecimal("39.99"))
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_SKU_CONFLICT"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should return 400 BAD REQUEST for invalid product data")
    void createProduct_InvalidData_ReturnsBadRequest() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("")
                .name("A")
                .categoryId(testCategory.getCategoryId())
                .price(new BigDecimal("-10.00"))
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("Should get product by ID successfully")
    void getProductById_Success() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("GET-BY-ID-SKU")
                .name("Get By ID Product")
                .description("Product for get by ID test")
                .categoryId(testCategory.getCategoryId())
                .price(new BigDecimal("59.99"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ProductResponse createdProduct = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), ProductResponse.class);

        mockMvc.perform(get("/api/v1/products/{productId}", createdProduct.getProductId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(createdProduct.getProductId()))
                .andExpect(jsonPath("$.sku").value("GET-BY-ID-SKU"));
    }

    @Test
    @DisplayName("Should return 404 NOT FOUND for non-existent product")
    void getProductById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/{productId}", "non-existent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Should list products with pagination")
    void listProducts_WithPagination_Success() throws Exception {
        for (int i = 1; i <= 15; i++) {
            CreateProductRequest request = CreateProductRequest.builder()
                    .sku("PAGINATE-SKU-" + String.format("%03d", i))
                    .name("Paginated Product " + i)
                    .categoryId(testCategory.getCategoryId())
                    .price(new BigDecimal("10.00").add(new BigDecimal(i)))
                    .build();

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/products")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/v1/products")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("Should deactivate product successfully")
    void deactivateProduct_Success() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("DEACTIVATE-SKU")
                .name("Product to Deactivate")
                .categoryId(testCategory.getCategoryId())
                .price(new BigDecimal("19.99"))
                .active(true)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        ProductResponse createdProduct = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), ProductResponse.class);

        mockMvc.perform(patch("/api/v1/products/{productId}/deactivate", createdProduct.getProductId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("Should include correlation ID in response header")
    void correlationId_IncludedInResponse() throws Exception {
        String customCorrelationId = "test-correlation-123";

        mockMvc.perform(get("/api/v1/products")
                        .header("X-Correlation-Id", customCorrelationId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", customCorrelationId));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
