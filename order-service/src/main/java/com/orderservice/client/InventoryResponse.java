package com.orderservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {

    private String productId;
    private Integer stockQty;
    private Integer reservedQty;
    private Integer availableQty;
    private Instant updatedAt;
}
