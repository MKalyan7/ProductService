package com.productservice.service;

import com.productservice.dto.request.UpdateInventoryRequest;
import com.productservice.dto.response.InventoryResponse;
import com.productservice.entity.Inventory;
import com.productservice.exception.BusinessException;
import com.productservice.exception.ErrorCode;
import com.productservice.exception.ResourceNotFoundException;
import com.productservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryResponse getInventoryByProductId(String productId) {
        log.info("Fetching inventory for product ID: {}", productId);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INVENTORY_NOT_FOUND,
                        "Inventory not found for product ID: " + productId));

        return mapToResponse(inventory);
    }

    @Transactional
    public InventoryResponse updateInventory(String productId, UpdateInventoryRequest request) {
        log.info("Updating inventory for product ID: {} with stockQty: {}", productId, request.getStockQty());

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INVENTORY_NOT_FOUND,
                        "Inventory not found for product ID: " + productId));

        inventory.setStockQty(request.getStockQty());

        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Updated inventory for product ID: {}", productId);
        return mapToResponse(updatedInventory);
    }

    @Transactional
    public InventoryResponse reserveStock(String productId, int qty) {
        log.info("Reserving {} units for product ID: {}", qty, productId);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INVENTORY_NOT_FOUND,
                        "Inventory not found for product ID: " + productId));

        int availableQty = inventory.getAvailableQty();
        if (availableQty < qty) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                    "Insufficient stock. Available: " + availableQty + ", Requested: " + qty);
        }

        inventory.setReservedQty(inventory.getReservedQty() + qty);

        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Reserved {} units for product ID: {}. New reserved qty: {}",
                qty, productId, updatedInventory.getReservedQty());
        return mapToResponse(updatedInventory);
    }

    @Transactional
    public InventoryResponse releaseStock(String productId, int qty) {
        log.info("Releasing {} units for product ID: {}", qty, productId);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INVENTORY_NOT_FOUND,
                        "Inventory not found for product ID: " + productId));

        int currentReserved = inventory.getReservedQty() != null ? inventory.getReservedQty() : 0;
        if (qty > currentReserved) {
            throw new BusinessException(ErrorCode.INVALID_RELEASE_QUANTITY,
                    "Cannot release " + qty + " units. Only " + currentReserved + " units are reserved");
        }

        inventory.setReservedQty(currentReserved - qty);

        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Released {} units for product ID: {}. New reserved qty: {}",
                qty, productId, updatedInventory.getReservedQty());
        return mapToResponse(updatedInventory);
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .productId(inventory.getProductId())
                .stockQty(inventory.getStockQty())
                .reservedQty(inventory.getReservedQty())
                .availableQty(inventory.getAvailableQty())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
