package com.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String orderId;
    private String customerId;
    private String status;
    private List<OrderItemResponse> items;
    private BigDecimal orderTotal;
    private String currency;
    private Instant createdAt;
    private Instant updatedAt;
}
