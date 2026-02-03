package com.productservice.service;

import com.productservice.dto.request.UpdateInventoryRequest;
import com.productservice.dto.response.InventoryResponse;
import com.productservice.entity.Inventory;
import com.productservice.exception.BusinessException;
import com.productservice.exception.ResourceNotFoundException;
import com.productservice.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory testInventory;
    private String productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID().toString();

        testInventory = Inventory.builder()
                .id("mongo-id-123")
                .productId(productId)
                .stockQty(100)
                .reservedQty(20)
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should get inventory by product ID successfully")
    void getInventoryByProductId_Success() {
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(testInventory));

        InventoryResponse response = inventoryService.getInventoryByProductId(productId);

        assertThat(response).isNotNull();
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getStockQty()).isEqualTo(100);
        assertThat(response.getReservedQty()).isEqualTo(20);
        assertThat(response.getAvailableQty()).isEqualTo(80);

        verify(inventoryRepository).findByProductId(productId);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when inventory not found")
    void getInventoryByProductId_NotFound_ThrowsResourceNotFoundException() {
        String nonExistentId = "non-existent-id";
        when(inventoryRepository.findByProductId(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getInventoryByProductId(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");

        verify(inventoryRepository).findByProductId(nonExistentId);
    }

    @Test
    @DisplayName("Should update inventory stock successfully")
    void updateInventory_Success() {
        UpdateInventoryRequest request = UpdateInventoryRequest.builder()
                .stockQty(150)
                .build();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory saved = invocation.getArgument(0);
            saved.setStockQty(150);
            return saved;
        });

        InventoryResponse response = inventoryService.updateInventory(productId, request);

        assertThat(response).isNotNull();
        assertThat(response.getStockQty()).isEqualTo(150);

        verify(inventoryRepository).findByProductId(productId);
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should reserve stock successfully when available")
    void reserveStock_Success() {
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory saved = invocation.getArgument(0);
            return saved;
        });

        InventoryResponse response = inventoryService.reserveStock(productId, 30);

        assertThat(response).isNotNull();
        assertThat(response.getReservedQty()).isEqualTo(50);

        verify(inventoryRepository).findByProductId(productId);
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw BusinessException when insufficient stock for reservation")
    void reserveStock_InsufficientStock_ThrowsBusinessException() {
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(testInventory));

        assertThatThrownBy(() -> inventoryService.reserveStock(productId, 100))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");

        verify(inventoryRepository).findByProductId(productId);
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should release stock successfully")
    void releaseStock_Success() {
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory saved = invocation.getArgument(0);
            return saved;
        });

        InventoryResponse response = inventoryService.releaseStock(productId, 10);

        assertThat(response).isNotNull();
        assertThat(response.getReservedQty()).isEqualTo(10);

        verify(inventoryRepository).findByProductId(productId);
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw BusinessException when releasing more than reserved")
    void releaseStock_ExceedsReserved_ThrowsBusinessException() {
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(testInventory));

        assertThatThrownBy(() -> inventoryService.releaseStock(productId, 50))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot release");

        verify(inventoryRepository).findByProductId(productId);
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }
}
