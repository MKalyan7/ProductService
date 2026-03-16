package com.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentservice.dto.request.CreatePaymentRequest;
import com.paymentservice.dto.request.PaymentFailureRequest;
import com.paymentservice.dto.request.PaymentRefundRequest;
import com.paymentservice.dto.request.PaymentSuccessRequest;
import com.paymentservice.dto.response.PageResponse;
import com.paymentservice.dto.response.PaymentResponse;
import com.paymentservice.entity.PaymentMethod;
import com.paymentservice.entity.PaymentStatus;
import com.paymentservice.exception.ErrorCode;
import com.paymentservice.exception.InvalidPaymentStateException;
import com.paymentservice.exception.ResourceNotFoundException;
import com.paymentservice.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private PaymentResponse sampleResponse() {
        return PaymentResponse.builder()
                .paymentId("PAY-000001")
                .orderId("ORD-1001")
                .customerId("CUST-001")
                .amount(BigDecimal.valueOf(149.99))
                .currency("USD")
                .method("CREDIT_CARD")
                .status("INITIATED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/payments - should create payment")
    void shouldCreatePayment() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId("ORD-1001")
                .customerId("CUST-001")
                .amount(BigDecimal.valueOf(149.99))
                .currency("USD")
                .method(PaymentMethod.CREDIT_CARD)
                .build();

        when(paymentService.createPayment(any(CreatePaymentRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value("PAY-000001"))
                .andExpect(jsonPath("$.orderId").value("ORD-1001"))
                .andExpect(jsonPath("$.status").value("INITIATED"));
    }

    @Test
    @DisplayName("POST /api/v1/payments - should fail validation with missing fields")
    void shouldFailValidationWithMissingFields() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder().build();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId} - should return payment")
    void shouldGetPaymentById() throws Exception {
        when(paymentService.getPaymentById("PAY-000001")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/payments/PAY-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("PAY-000001"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId} - should return 404 for unknown ID")
    void shouldReturn404ForUnknownPayment() throws Exception {
        when(paymentService.getPaymentById("PAY-999999"))
                .thenThrow(new ResourceNotFoundException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Payment not found with ID: PAY-999999"));

        mockMvc.perform(get("/api/v1/payments/PAY-999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/payments - should list payments")
    void shouldListPayments() throws Exception {
        PageResponse<PaymentResponse> pageResponse = PageResponse.<PaymentResponse>builder()
                .content(List.of(sampleResponse()))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(paymentService.listPayments(any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/payments")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].paymentId").value("PAY-000001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{paymentId}/process - should process payment")
    void shouldProcessPayment() throws Exception {
        PaymentResponse response = sampleResponse();
        response.setStatus("PROCESSING");
        when(paymentService.processPayment("PAY-000001")).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/PAY-000001/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{paymentId}/success - should mark success")
    void shouldMarkPaymentSuccess() throws Exception {
        PaymentResponse response = sampleResponse();
        response.setStatus("SUCCESS");
        response.setProviderReference("TXN-ABC-12345");

        PaymentSuccessRequest request = PaymentSuccessRequest.builder()
                .providerReference("TXN-ABC-12345")
                .build();

        when(paymentService.markPaymentSuccess(eq("PAY-000001"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/PAY-000001/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.providerReference").value("TXN-ABC-12345"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{paymentId}/fail - should mark failed")
    void shouldMarkPaymentFailed() throws Exception {
        PaymentResponse response = sampleResponse();
        response.setStatus("FAILED");
        response.setFailureReason("Card declined");

        when(paymentService.markPaymentFailed(eq("PAY-000001"), any())).thenReturn(response);

        PaymentFailureRequest request = PaymentFailureRequest.builder()
                .failureReason("Card declined")
                .build();

        mockMvc.perform(post("/api/v1/payments/PAY-000001/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Card declined"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{paymentId}/refund - should refund payment")
    void shouldRefundPayment() throws Exception {
        PaymentResponse response = sampleResponse();
        response.setStatus("REFUNDED");

        when(paymentService.refundPayment(eq("PAY-000001"), any())).thenReturn(response);

        PaymentRefundRequest request = PaymentRefundRequest.builder()
                .reason("Customer cancellation")
                .build();

        mockMvc.perform(post("/api/v1/payments/PAY-000001/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{paymentId}/refund - should return 400 for invalid state transition")
    void shouldReturn400ForInvalidStateTransition() throws Exception {
        when(paymentService.refundPayment(eq("PAY-000001"), any()))
                .thenThrow(new InvalidPaymentStateException(
                        "Cannot transition payment PAY-000001 from INITIATED to REFUNDED"));

        mockMvc.perform(post("/api/v1/payments/PAY-000001/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAYMENT_STATE"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/order/{orderId} - should get payments by order")
    void shouldGetPaymentsByOrderId() throws Exception {
        when(paymentService.getPaymentsByOrderId("ORD-1001")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/payments/order/ORD-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].orderId").value("ORD-1001"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/internal/order/{orderId}/latest - should get latest payment")
    void shouldGetLatestPaymentByOrderId() throws Exception {
        when(paymentService.getLatestPaymentByOrderId("ORD-1001")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/payments/internal/order/ORD-1001/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-1001"));
    }
}
