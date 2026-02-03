package com.productservice.service;

import com.productservice.dto.request.CreateProductRequest;
import com.productservice.dto.request.UpdateProductRequest;
import com.productservice.dto.response.PageResponse;
import com.productservice.dto.response.ProductResponse;
import com.productservice.entity.Inventory;
import com.productservice.entity.Product;
import com.productservice.exception.ConflictException;
import com.productservice.exception.ErrorCode;
import com.productservice.exception.ResourceNotFoundException;
import com.productservice.repository.InventoryRepository;
import com.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("Creating product with SKU: {}", request.getSku());

        if (productRepository.existsBySku(request.getSku())) {
            throw new ConflictException(ErrorCode.PRODUCT_SKU_CONFLICT,
                    "Product with SKU '" + request.getSku() + "' already exists");
        }

        String productId = UUID.randomUUID().toString();

        Product product = Product.builder()
                .productId(productId)
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .categoryId(request.getCategoryId())
                .price(request.getPrice())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Product savedProduct = productRepository.save(product);

        Inventory inventory = Inventory.builder()
                .productId(productId)
                .stockQty(request.getInitialStockQty() != null ? request.getInitialStockQty() : 0)
                .reservedQty(0)
                .build();

        inventoryRepository.save(inventory);

        log.info("Created product with ID: {}", productId);
        return mapToResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(String productId, UpdateProductRequest request) {
        log.info("Updating product with ID: {}", productId);

        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND,
                        "Product not found with ID: " + productId));

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getCategoryId() != null) {
            product.setCategoryId(request.getCategoryId());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCurrency() != null) {
            product.setCurrency(request.getCurrency());
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Updated product with ID: {}", productId);
        return mapToResponse(updatedProduct);
    }

    public ProductResponse getProductById(String productId) {
        log.info("Fetching product with ID: {}", productId);

        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND,
                        "Product not found with ID: " + productId));

        return mapToResponse(product);
    }

    public ProductResponse getProductBySku(String sku) {
        log.info("Fetching product with SKU: {}", sku);

        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND,
                        "Product not found with SKU: " + sku));

        return mapToResponse(product);
    }

    public PageResponse<ProductResponse> listProducts(int page, int size, String sortBy, String sortDir,
                                                       String categoryId, Boolean active, String q,
                                                       BigDecimal minPrice, BigDecimal maxPrice) {
        log.info("Listing products with filters - page: {}, size: {}, categoryId: {}, active: {}, q: {}",
                page, size, categoryId, active, q);

        Sort sort = sortDir != null && sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy != null ? sortBy : "createdAt").descending()
                : Sort.by(sortBy != null ? sortBy : "createdAt").ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Product> productPage;

        if (q != null && !q.isBlank()) {
            productPage = productRepository.searchByText(q, pageable);
        } else {
            productPage = findProductsWithFilters(categoryId, active, minPrice, maxPrice, pageable);
        }

        return PageResponse.<ProductResponse>builder()
                .content(productPage.getContent().stream().map(this::mapToResponse).toList())
                .page(productPage.getNumber())
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .first(productPage.isFirst())
                .last(productPage.isLast())
                .build();
    }

    private Page<Product> findProductsWithFilters(String categoryId, Boolean active,
                                                   BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        if (categoryId != null && active != null) {
            return productRepository.findByCategoryIdAndActive(categoryId, active, pageable);
        } else if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId, pageable);
        } else if (active != null) {
            return productRepository.findByActive(active, pageable);
        } else {
            return productRepository.findAll(pageable);
        }
    }

    @Transactional
    public ProductResponse deactivateProduct(String productId) {
        log.info("Deactivating product with ID: {}", productId);

        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND,
                        "Product not found with ID: " + productId));

        product.setActive(false);
        Product updatedProduct = productRepository.save(product);

        log.info("Deactivated product with ID: {}", productId);
        return mapToResponse(updatedProduct);
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .productId(product.getProductId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategoryId())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
