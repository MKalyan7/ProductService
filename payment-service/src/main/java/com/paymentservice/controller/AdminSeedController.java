package com.paymentservice.controller;

import com.paymentservice.entity.SeedRun;
import com.paymentservice.seed.PaymentSeedService;
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
@Tag(name = "Admin Seed", description = "Admin endpoints for payment data seeding operations")
public class AdminSeedController {

    private final PaymentSeedService paymentSeedService;

    @Value("${app.seed.endpoint.enabled:false}")
    private boolean seedEndpointEnabled;

    @Value("${app.seed.default-count:500}")
    private int defaultCount;

    @PostMapping("/payments")
    @Operation(summary = "Seed payments",
            description = "Generates and inserts sample payment data. " +
                    "Requires app.seed.endpoint.enabled=true in configuration.")
    public ResponseEntity<?> seedPayments(
            @Parameter(description = "Number of payments to generate (default: 500)")
            @RequestParam(required = false) Integer count,
            @Parameter(description = "Random seed for deterministic generation")
            @RequestParam(required = false) Long seed,
            @Parameter(description = "If true, deletes existing seeded payments before re-seeding (default: false)")
            @RequestParam(defaultValue = "false") boolean reset) {

        if (!seedEndpointEnabled) {
            log.warn("Seed endpoint called but is disabled. Set app.seed.endpoint.enabled=true to enable.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "Seed endpoint is disabled",
                            "message", "Set app.seed.endpoint.enabled=true in application properties to enable this endpoint"
                    ));
        }

        int paymentCount = count != null ? Math.max(10, Math.min(5000, count)) : defaultCount;
        long seedValue = seed != null ? seed : System.currentTimeMillis();

        log.info("Seed endpoint called: count={}, seed={}, reset={}", paymentCount, seedValue, reset);

        SeedRun result = paymentSeedService.seed(paymentCount, seedValue, reset);

        if ("FAILED".equals(result.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/payments/status")
    @Operation(summary = "Get seed status",
            description = "Returns current payment counts and information about the last seed run")
    public ResponseEntity<PaymentSeedService.SeedStatus> getStatus() {
        return ResponseEntity.ok(paymentSeedService.getSeedStatus());
    }
}
