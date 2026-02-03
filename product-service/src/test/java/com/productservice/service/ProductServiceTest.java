package com.productservice.service;

import com.productservice.dto.request.CreateProductRequest;
import com.productservice.dto.request.UpdateProductRequest;
import com.productservice.dto.response.ProductResponse;
import com.productservice.entity.Inventory;
import com.productservice.entity.Product;
import com.productservice.exception.ConflictException;
import com.productservice.exception.ResourceNotFoundException;
import com.productservice.repository.InventoryRepository;
import com.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private CreateProductRequest createRequest;
    private UpdateProductRequest updateRequest;

    @BeforeEach
    void setUp() {
        String productId = UUID.randomUUID().toString();

        testProduct = Product.builder()
                .id("mongo-id-123")
                .productId(productId)
                .sku("TEST-SKU-001")
                .name("Test Product")
                .description("Test Description")
                .categoryId("cat-123")
                .price(new BigDecimal("99.99"))
                .currency("USD")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        createRequest = CreateProductRequest.builder()
                .sku("TEST-SKU-001")
                .name("Test Product")
                .description("Test Description")
                .categoryId("cat-123")
                .price(new BigDecimal("99.99"))
                .currency("USD")
                .active(true)
                .initialStockQty(100)
                .build();

        updateRequest = UpdateProductRequest.builder()
                .name("Updated Product")
                .price(new BigDecimal("149.99"))
                .build();
    }

    @Test
    @DisplayName("Should create product successfully")
    void createProduct_Success() {
        when(productRepository.existsBySku(createRequest.getSku())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(new Inventory());

        ProductResponse response = productService.createProduct(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getSku()).isEqualTo(testProduct.getSku());
        assertThat(response.getName()).isEqualTo(testProduct.getName());
        assertThat(response.getPrice()).isEqualTo(testProduct.getPrice());

        verify(productRepository).existsBySku(createRequest.getSku());
        verify(productRepository).save(any(Product.class));
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when SKU already exists")
    void createProduct_DuplicateSku_ThrowsConflictException() {
        when(productRepository.existsBySku(createRequest.getSku())).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(createRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(productRepository).existsBySku(createRequest.getSku());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Should update product successfully")
    void updateProduct_Success() {
        when(productRepository.findByProductId(testProduct.getProductId())).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        ProductResponse response = productService.updateProduct(testProduct.getProductId(), updateRequest);

        assertThat(response).isNotNull();
        verify(productRepository).findByProductId(testProduct.getProductId());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when updating non-existent product")
    void updateProduct_NotFound_ThrowsResourceNotFoundException() {
        String nonExistentId = "non-existent-id";
        when(productRepository.findByProductId(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(nonExistentId, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");

        verify(productRepository).findByProductId(nonExistentId);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Should get product by ID successfully")
    void getProductById_Success() {
        when(productRepository.findByProductId(testProduct.getProductId())).thenReturn(Optional.of(testProduct));

        ProductResponse response = productService.getProductById(testProduct.getProductId());

        assertThat(response).isNotNull();
        assertThat(response.getProductId()).isEqualTo(testProduct.getProductId());
        assertThat(response.getSku()).isEqualTo(testProduct.getSku());

        verify(productRepository).findByProductId(testProduct.getProductId());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when product not found by ID")
    void getProductById_NotFound_ThrowsResourceNotFoundException() {
        String nonExistentId = "non-existent-id";
        when(productRepository.findByProductId(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");

        verify(productRepository).findByProductId(nonExistentId);
    }

    @Test
    @DisplayName("Should get product by SKU successfully")
    void getProductBySku_Success() {
        when(productRepository.findBySku(testProduct.getSku())).thenReturn(Optional.of(testProduct));

        ProductResponse response = productService.getProductBySku(testProduct.getSku());

        assertThat(response).isNotNull();
        assertThat(response.getSku()).isEqualTo(testProduct.getSku());

        verify(productRepository).findBySku(testProduct.getSku());
    }

    @Test
    @DisplayName("Should deactivate product successfully")
    void deactivateProduct_Success() {
        when(productRepository.findByProductId(testProduct.getProductId())).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setActive(false);
            return saved;
        });

        ProductResponse response = productService.deactivateProduct(testProduct.getProductId());

        assertThat(response).isNotNull();
        assertThat(response.isActive()).isFalse();

        verify(productRepository).findByProductId(testProduct.getProductId());
        verify(productRepository).save(any(Product.class));
    }
}
