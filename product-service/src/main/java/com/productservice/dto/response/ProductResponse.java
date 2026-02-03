package com.productservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String productId;
    private String sku;
    private String name;
    private String description;
    private String categoryId;
    private BigDecimal price;
    private String currency;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
