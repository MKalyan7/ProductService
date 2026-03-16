package com.paymentservice.controller;

import com.paymentservice.dto.request.CreatePaymentRequest;
import com.paymentservice.dto.request.PaymentFailureRequest;
import com.paymentservice.dto.request.PaymentRefundRequest;
import com.paymentservice.dto.request.PaymentSuccessRequest;
import com.paymentservice.dto.response.PageResponse;
import com.paymentservice.dto.response.PaymentResponse;
import com.paymentservice.entity.PaymentStatus;
import com.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment management APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create a new payment",
            description = "Creates a new payment in INITIATED status for the given order")
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse response = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment by ID", description = "Retrieves a payment by its unique payment ID")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @Parameter(description = "Payment ID") @PathVariable String paymentId) {
        PaymentResponse response = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List payments",
            description = "Lists payments with pagination, sorting, and optional filters")
    public ResponseEntity<PageResponse<PaymentResponse>> listPayments(
            @Parameter(description = "Filter by customer ID") @RequestParam(required = false) String customerId,
            @Parameter(description = "Filter by order ID") @RequestParam(required = false) String orderId,
            @Parameter(description = "Filter by payment status") @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(required = false) String sort,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        PageResponse<PaymentResponse> response = paymentService.listPayments(
                customerId, orderId, status, page, size, sort, sortDir);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/process")
    @Operation(summary = "Start processing payment",
            description = "Moves payment from INITIATED to PROCESSING status")
    public ResponseEntity<PaymentResponse> processPayment(
            @Parameter(description = "Payment ID") @PathVariable String paymentId) {
        PaymentResponse response = paymentService.processPayment(paymentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/success")
    @Operation(summary = "Mark payment as successful",
            description = "Moves payment from INITIATED or PROCESSING to SUCCESS status")
    public ResponseEntity<PaymentResponse> markPaymentSuccess(
            @Parameter(description = "Payment ID") @PathVariable String paymentId,
            @RequestBody(required = false) PaymentSuccessRequest request) {
        PaymentResponse response = paymentService.markPaymentSuccess(paymentId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/fail")
    @Operation(summary = "Mark payment as failed",
            description = "Moves payment from INITIATED or PROCESSING to FAILED status")
    public ResponseEntity<PaymentResponse> markPaymentFailed(
            @Parameter(description = "Payment ID") @PathVariable String paymentId,
            @RequestBody(required = false) PaymentFailureRequest request) {
        PaymentResponse response = paymentService.markPaymentFailed(paymentId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a payment",
            description = "Moves payment from SUCCESS to REFUNDED status")
    public ResponseEntity<PaymentResponse> refundPayment(
            @Parameter(description = "Payment ID") @PathVariable String paymentId,
            @RequestBody(required = false) PaymentRefundRequest request) {
        PaymentResponse response = paymentService.refundPayment(paymentId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payments by order ID",
            description = "Retrieves all payments for a given order")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrderId(
            @Parameter(description = "Order ID") @PathVariable String orderId) {
        List<PaymentResponse> response = paymentService.getPaymentsByOrderId(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/internal/order/{orderId}/latest")
    @Operation(summary = "Get latest payment for order (internal)",
            description = "Returns the most recent payment for an order. Designed for inter-service use.")
    public ResponseEntity<PaymentResponse> getLatestPaymentByOrderId(
            @Parameter(description = "Order ID") @PathVariable String orderId) {
        PaymentResponse response = paymentService.getLatestPaymentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }
}
