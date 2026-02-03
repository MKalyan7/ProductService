package com.productservice.controller;

import com.productservice.dto.request.CreateProductRequest;
import com.productservice.dto.request.UpdateProductRequest;
import com.productservice.dto.response.PageResponse;
import com.productservice.dto.response.ProductResponse;
import com.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management APIs")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a new product", description = "Creates a new product and its inventory record")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Update a product", description = "Updates an existing product by its ID")
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "Product ID") @PathVariable String productId,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductResponse response = productService.updateProduct(productId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get product by ID", description = "Retrieves a product by its unique product ID")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "Product ID") @PathVariable String productId) {
        ProductResponse response = productService.getProductById(productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List products", description = "Lists products with pagination, sorting, and optional filters")
    public ResponseEntity<PageResponse<ProductResponse>> listProducts(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(required = false) String sort,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(required = false, defaultValue = "asc") String sortDir,
            @Parameter(description = "Filter by category ID") @RequestParam(required = false) String categoryId,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Search query for name/description") @RequestParam(required = false) String q,
            @Parameter(description = "Minimum price filter") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price filter") @RequestParam(required = false) BigDecimal maxPrice) {
        PageResponse<ProductResponse> response = productService.listProducts(
                page, size, sort, sortDir, categoryId, active, q, minPrice, maxPrice);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU", description = "Retrieves a product by its unique SKU")
    public ResponseEntity<ProductResponse> getProductBySku(
            @Parameter(description = "Product SKU") @PathVariable String sku) {
        ProductResponse response = productService.getProductBySku(sku);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{productId}/deactivate")
    @Operation(summary = "Deactivate a product", description = "Sets the product's active status to false")
    public ResponseEntity<ProductResponse> deactivateProduct(
            @Parameter(description = "Product ID") @PathVariable String productId) {
        ProductResponse response = productService.deactivateProduct(productId);
        return ResponseEntity.ok(response);
    }
}
