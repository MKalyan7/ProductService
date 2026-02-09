package com.orderservice.controller;

import com.orderservice.entity.SeedRun;
import com.orderservice.seed.OrderSeedService;
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
@Tag(name = "Admin Seed", description = "Admin endpoints for order data seeding operations")
public class AdminSeedController {

    private final OrderSeedService orderSeedService;

    @Value("${app.seed.endpoint.enabled:false}")
    private boolean seedEndpointEnabled;

    @Value("${app.seed.default-count:2000}")
    private int defaultCount;

    @Value("${app.seed.reserve-inventory:false}")
    private boolean defaultReserveInventory;

    @PostMapping("/orders")
    @Operation(summary = "Seed orders",
            description = "Generates and inserts realistic order data using products from product-service. " +
                    "Requires app.seed.endpoint.enabled=true in configuration.")
    public ResponseEntity<?> seedOrders(
            @Parameter(description = "Number of orders to generate (default: 2000, range: 500-3000)")
            @RequestParam(required = false) Integer count,
            @Parameter(description = "Random seed for deterministic generation")
            @RequestParam(required = false) Long seed,
            @Parameter(description = "If true, deletes existing seeded orders before re-seeding (default: false)")
            @RequestParam(defaultValue = "false") boolean reset,
            @Parameter(description = "If true, calls product-service to reserve inventory for non-cancelled orders (default: false)")
            @RequestParam(required = false) Boolean reserveInventory) {

        if (!seedEndpointEnabled) {
            log.warn("Seed endpoint called but is disabled. Set app.seed.endpoint.enabled=true to enable.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "Seed endpoint is disabled",
                            "message", "Set app.seed.endpoint.enabled=true in application properties to enable this endpoint"
                    ));
        }

        int orderCount = count != null ? Math.max(500, Math.min(3000, count)) : defaultCount;
        long seedValue = seed != null ? seed : System.currentTimeMillis();
        boolean reserve = reserveInventory != null ? reserveInventory : defaultReserveInventory;

        log.info("Seed endpoint called: count={}, seed={}, reset={}, reserveInventory={}",
                orderCount, seedValue, reset, reserve);

        SeedRun result = orderSeedService.seed(orderCount, seedValue, reset, reserve);

        if ("FAILED".equals(result.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    @Operation(summary = "Get seed status",
            description = "Returns current order counts and information about the last seed run")
    public ResponseEntity<OrderSeedService.SeedStatus> getStatus() {
        return ResponseEntity.ok(orderSeedService.getSeedStatus());
    }
}
