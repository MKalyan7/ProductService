package com.orderservice.client;

import com.orderservice.exception.ErrorCode;
import com.orderservice.exception.ProductServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
@Slf4j
public class ProductServiceClient {

    private final WebClient webClient;

    public ProductServiceClient(WebClient productServiceWebClient) {
        this.webClient = productServiceWebClient;
    }

    public ProductResponse getProduct(String productId) {
        log.info("Fetching product from product-service: {}", productId);
        try {
            return webClient.get()
                    .uri("/api/v1/products/{productId}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(ProductServiceErrorResponse.class)
                                    .map(err -> mapClientError(err, productId, response.statusCode())))
                    .bodyToMono(ProductResponse.class)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .filter(this::isTransientError))
                    .block();
        } catch (ProductServiceException e) {
            throw e;
        } catch (WebClientRequestException e) {
            log.error("Product service connection error for product {}: {}", productId, e.getMessage());
            throw new ProductServiceException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Product service is unavailable: " + e.getMessage());
        } catch (Exception e) {
            if (e.getCause() instanceof ProductServiceException pse) {
                throw pse;
            }
            log.error("Error calling product service for product {}: {}", productId, e.getMessage());
            throw new ProductServiceException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Product service is unavailable");
        }
    }

    public InventoryResponse reserveInventory(String productId, int qty) {
        log.info("Reserving {} units for product {} via product-service", qty, productId);
        try {
            return webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/inventory/{productId}/reserve")
                            .queryParam("qty", qty)
                            .build(productId))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(ProductServiceErrorResponse.class)
                                    .map(err -> mapInventoryError(err, productId)))
                    .bodyToMono(InventoryResponse.class)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .filter(this::isTransientError))
                    .block();
        } catch (ProductServiceException e) {
            throw e;
        } catch (WebClientRequestException e) {
            log.error("Product service connection error reserving inventory for {}: {}", productId, e.getMessage());
            throw new ProductServiceException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Product service is unavailable: " + e.getMessage());
        } catch (Exception e) {
            if (e.getCause() instanceof ProductServiceException pse) {
                throw pse;
            }
            log.error("Error reserving inventory for product {}: {}", productId, e.getMessage());
            throw new ProductServiceException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                    "Product service is unavailable");
        }
    }

    public void releaseInventory(String productId, int qty) {
        log.info("Releasing {} units for product {} via product-service", qty, productId);
        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/inventory/{productId}/release")
                            .queryParam("qty", qty)
                            .build(productId))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(ProductServiceErrorResponse.class)
                                    .map(err -> mapInventoryError(err, productId)))
                    .bodyToMono(InventoryResponse.class)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .filter(this::isTransientError))
                    .block();
        } catch (ProductServiceException e) {
            log.warn("Failed to release inventory for product {}: {}", productId, e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to release inventory for product {}: {}", productId, e.getMessage());
        }
    }

    private boolean isTransientError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        return throwable instanceof WebClientRequestException;
    }

    private ProductServiceException mapClientError(ProductServiceErrorResponse err, String productId,
                                                   HttpStatusCode statusCode) {
        if (statusCode.value() == 404) {
            return new ProductServiceException(ErrorCode.PRODUCT_NOT_FOUND,
                    "Product not found: " + productId);
        }
        String errorCode = err != null ? err.getErrorCode() : "";
        if ("PRODUCT_INACTIVE".equals(errorCode)) {
            return new ProductServiceException(ErrorCode.PRODUCT_INACTIVE,
                    "Product is inactive: " + productId);
        }
        return new ProductServiceException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                "Product service returned error for product: " + productId);
    }

    private ProductServiceException mapInventoryError(ProductServiceErrorResponse err, String productId) {
        String errorCode = err != null ? err.getErrorCode() : "";
        if ("INSUFFICIENT_STOCK".equals(errorCode)) {
            return new ProductServiceException(ErrorCode.OUT_OF_STOCK,
                    "Insufficient stock for product: " + productId);
        }
        if ("INVENTORY_NOT_FOUND".equals(errorCode)) {
            return new ProductServiceException(ErrorCode.PRODUCT_NOT_FOUND,
                    "Inventory not found for product: " + productId);
        }
        return new ProductServiceException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                "Product service inventory error for product: " + productId);
    }
}
