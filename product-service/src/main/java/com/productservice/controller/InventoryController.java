package com.productservice.controller;

import com.productservice.dto.request.UpdateInventoryRequest;
import com.productservice.dto.response.InventoryResponse;
import com.productservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management APIs")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    @Operation(summary = "Get inventory by product ID", description = "Retrieves inventory information for a product")
    public ResponseEntity<InventoryResponse> getInventory(
            @Parameter(description = "Product ID") @PathVariable String productId) {
        InventoryResponse response = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Update inventory stock", description = "Updates the stock quantity for a product")
    public ResponseEntity<InventoryResponse> updateInventory(
            @Parameter(description = "Product ID") @PathVariable String productId,
            @Valid @RequestBody UpdateInventoryRequest request) {
        InventoryResponse response = inventoryService.updateInventory(productId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/reserve")
    @Operation(summary = "Reserve stock", description = "Reserves a quantity of stock if available")
    public ResponseEntity<InventoryResponse> reserveStock(
            @Parameter(description = "Product ID") @PathVariable String productId,
            @Parameter(description = "Quantity to reserve") @RequestParam int qty) {
        InventoryResponse response = inventoryService.reserveStock(productId, qty);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/release")
    @Operation(summary = "Release reserved stock", description = "Releases previously reserved stock")
    public ResponseEntity<InventoryResponse> releaseStock(
            @Parameter(description = "Product ID") @PathVariable String productId,
            @Parameter(description = "Quantity to release") @RequestParam int qty) {
        InventoryResponse response = inventoryService.releaseStock(productId, qty);
        return ResponseEntity.ok(response);
    }
}
