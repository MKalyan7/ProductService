package com.paymentservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
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

    private String status;
    private int recordsCreated;
    private long durationMs;
    private long seed;
    private String errorMessage;

    @CreatedDate
    private Instant createdAt;
}
