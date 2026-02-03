package com.productservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventory")
public class Inventory {

    @Id
    private String id;

    @Indexed(unique = true)
    private String productId;

    private Integer stockQty;

    @Builder.Default
    private Integer reservedQty = 0;

    @LastModifiedDate
    private Instant updatedAt;

    public Integer getAvailableQty() {
        int available = (stockQty != null ? stockQty : 0) - (reservedQty != null ? reservedQty : 0);
        return Math.max(available, 0);
    }
}
