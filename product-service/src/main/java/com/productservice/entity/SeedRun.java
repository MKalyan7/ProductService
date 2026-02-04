package com.productservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "seed_runs")
public class SeedRun {

    @Id
    private String id;

    private Instant lastSeedTime;

    private Long seed;

    private Integer productCount;

    private Integer categoryCount;

    private Long durationMs;

    private String status;

    private String message;
}
