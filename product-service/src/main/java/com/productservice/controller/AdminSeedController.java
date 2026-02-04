package com.productservice.controller;

import com.productservice.entity.SeedRun;
import com.productservice.service.SeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/seed")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Seed", description = "Admin endpoints for data seeding operations")
public class AdminSeedController {

    private final SeedService seedService;

    @Value("${app.seed.endpoint.enabled:false}")
    private boolean seedEndpointEnabled;

    @Value("${app.seed.default-count:1500}")
    private int defaultProductCount;

    @PostMapping
    @Operation(summary = "Seed database with test data",
            description = "Generates and inserts categories, products, and inventory records. " +
                    "Requires app.seed.endpoint.enabled=true in configuration.")
    public ResponseEntity<?> seed(
            @Parameter(description = "Number of products to generate (default: 1500)")
            @RequestParam(required = false) Integer count,
            @Parameter(description = "Random seed for deterministic generation (default: current timestamp)")
            @RequestParam(required = false) Long seed,
            @Parameter(description = "If true, clears existing data before seeding (default: false)")
            @RequestParam(defaultValue = "false") boolean reset) {

        if (!seedEndpointEnabled) {
            log.warn("Seed endpoint called but is disabled. Set app.seed.endpoint.enabled=true to enable.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "Seed endpoint is disabled",
                            "message", "Set app.seed.endpoint.enabled=true in application properties to enable this endpoint"
                    ));
        }

        int productCount = count != null ? count : defaultProductCount;
        long seedValue = seed != null ? seed : System.currentTimeMillis();

        log.info("Seed endpoint called: count={}, seed={}, reset={}", productCount, seedValue, reset);

        SeedRun result = seedService.seed(productCount, seedValue, reset);

        if ("FAILED".equals(result.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    @Operation(summary = "Get seed status",
            description = "Returns current data counts and information about the last seed run")
    public ResponseEntity<SeedService.SeedStatus> getStatus() {
        return ResponseEntity.ok(seedService.getSeedStatus());
    }
}
