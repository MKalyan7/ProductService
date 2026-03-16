package com.paymentservice.service;

import com.paymentservice.dto.request.CreatePaymentRequest;
import com.paymentservice.dto.request.PaymentFailureRequest;
import com.paymentservice.dto.request.PaymentRefundRequest;
import com.paymentservice.dto.request.PaymentSuccessRequest;
import com.paymentservice.dto.response.PageResponse;
import com.paymentservice.dto.response.PaymentResponse;
import com.paymentservice.entity.Payment;
import com.paymentservice.entity.PaymentMethod;
import com.paymentservice.entity.PaymentStatus;
import com.paymentservice.exception.InvalidPaymentStateException;
import com.paymentservice.exception.ResourceNotFoundException;
import com.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        samplePayment = Payment.builder()
                .id("mongo-id-1")
                .paymentId("PAY-000001")
                .orderId("ORD-1001")
                .customerId("CUST-001")
                .amount(BigDecimal.valueOf(149.99))
                .currency("USD")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.INITIATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Create Payment")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should create payment successfully")
        void shouldCreatePaymentSuccessfully() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId("ORD-1001")
                    .customerId("CUST-001")
                    .amount(BigDecimal.valueOf(149.99))
                    .currency("USD")
                    .method(PaymentMethod.CREDIT_CARD)
                    .build();

            when(paymentRepository.existsByPaymentId(anyString())).thenReturn(false);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment payment = invocation.getArgument(0);
                payment.setId("mongo-id-1");
                payment.setCreatedAt(Instant.now());
                payment.setUpdatedAt(Instant.now());
                return payment;
            });

            PaymentResponse response = paymentService.createPayment(request);

            assertThat(response).isNotNull();
            assertThat(response.getPaymentId()).startsWith("PAY-");
            assertThat(response.getOrderId()).isEqualTo("ORD-1001");
            assertThat(response.getCustomerId()).isEqualTo("CUST-001");
            assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(149.99));
            assertThat(response.getCurrency()).isEqualTo("USD");
            assertThat(response.getMethod()).isEqualTo("CREDIT_CARD");
            assertThat(response.getStatus()).isEqualTo("INITIATED");

            verify(paymentRepository).save(any(Payment.class));
        }
    }

    @Nested
    @DisplayName("Get Payment")
    class GetPaymentTests {

        @Test
        @DisplayName("Should get payment by ID")
        void shouldGetPaymentById() {
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            PaymentResponse response = paymentService.getPaymentById("PAY-000001");

            assertThat(response).isNotNull();
            assertThat(response.getPaymentId()).isEqualTo("PAY-000001");
            assertThat(response.getOrderId()).isEqualTo("ORD-1001");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException for unknown payment ID")
        void shouldThrowNotFoundForUnknownPaymentId() {
            when(paymentRepository.findByPaymentId("PAY-999999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentById("PAY-999999"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("PAY-999999");
        }
    }

    @Nested
    @DisplayName("Process Payment")
    class ProcessPaymentTests {

        @Test
        @DisplayName("Should process payment from INITIATED")
        void shouldProcessPaymentFromInitiated() {
            samplePayment.setStatus(PaymentStatus.INITIATED);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(samplePayment);

            PaymentResponse response = paymentService.processPayment("PAY-000001");

            assertThat(response.getStatus()).isEqualTo("PROCESSING");
            verify(paymentRepository).save(any(Payment.class));
        }

        @Test
        @DisplayName("Should reject process from PROCESSING")
        void shouldRejectProcessFromProcessing() {
            samplePayment.setStatus(PaymentStatus.PROCESSING);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            assertThatThrownBy(() -> paymentService.processPayment("PAY-000001"))
                    .isInstanceOf(InvalidPaymentStateException.class)
                    .hasMessageContaining("PROCESSING");
        }

        @Test
        @DisplayName("Should reject process from SUCCESS")
        void shouldRejectProcessFromSuccess() {
            samplePayment.setStatus(PaymentStatus.SUCCESS);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            assertThatThrownBy(() -> paymentService.processPayment("PAY-000001"))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }
    }

    @Nested
    @DisplayName("Mark Payment Success")
    class SuccessPaymentTests {

        @Test
        @DisplayName("Should mark payment success from INITIATED")
        void shouldMarkSuccessFromInitiated() {
            samplePayment.setStatus(PaymentStatus.INITIATED);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(samplePayment);

            PaymentSuccessRequest request = PaymentSuccessRequest.builder()
                    .providerReference("TXN-ABC-12345")
                    .build();

            PaymentResponse response = paymentService.markPaymentSuccess("PAY-000001", request);

            assertThat(response.getStatus()).isEqualTo("SUCCESS");
            assertThat(samplePayment.getProviderReference()).isEqualTo("TXN-ABC-12345");
        }

        @Test
        @DisplayName("Should mark payment success from PROCESSING")
        void shouldMarkSuccessFromProcessing() {
            samplePayment.setStatus(PaymentStatus.PROCESSING);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(samplePayment);

            PaymentResponse response = paymentService.markPaymentSuccess("PAY-000001", null);

            assertThat(response.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("Should reject success from FAILED")
        void shouldRejectSuccessFromFailed() {
            samplePayment.setStatus(PaymentStatus.FAILED);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            assertThatThrownBy(() -> paymentService.markPaymentSuccess("PAY-000001", null))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }

        @Test
        @DisplayName("Should reject success from REFUNDED")
        void shouldRejectSuccessFromRefunded() {
            samplePayment.setStatus(PaymentStatus.REFUNDED);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            assertThatThrownBy(() -> paymentService.markPaymentSuccess("PAY-000001", null))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }
    }

    @Nested
    @DisplayName("Mark Payment Failed")
    class FailPaymentTests {

        @Test
        @DisplayName("Should mark payment failed from INITIATED")
        void shouldMarkFailedFromInitiated() {
            samplePayment.setStatus(PaymentStatus.INITIATED);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(samplePayment);

            PaymentFailureRequest request = PaymentFailureRequest.builder()
                    .failureReason("Card declined")
                    .build();

            PaymentResponse response = paymentService.markPaymentFailed("PAY-000001", request);

            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(samplePayment.getFailureReason()).isEqualTo("Card declined");
        }

        @Test
        @DisplayName("Should mark payment failed from PROCESSING")
        void shouldMarkFailedFromProcessing() {
            samplePayment.setStatus(PaymentStatus.PROCESSING);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(samplePayment);

            PaymentResponse response = paymentService.markPaymentFailed("PAY-000001", null);

            assertThat(response.getStatus()).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("Should reject fail from SUCCESS")
        void shouldRejectFailFromSuccess() {
            samplePayment.setStatus(PaymentStatus.SUCCESS);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            assertThatThrownBy(() -> paymentService.markPaymentFailed("PAY-000001", null))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }
    }

    @Nested
    @DisplayName("Refund Payment")
    class RefundPaymentTests {

        @Test
        @DisplayName("Should refund payment from SUCCESS")
        void shouldRefundFromSuccess() {
            samplePayment.setStatus(PaymentStatus.SUCCESS);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(samplePayment);

            PaymentRefundRequest request = PaymentRefundRequest.builder()
                    .reason("Customer cancellation")
                    .build();

            PaymentResponse response = paymentService.refundPayment("PAY-000001", request);

            assertThat(response.getStatus()).isEqualTo("REFUNDED");
            assertThat(samplePayment.getFailureReason()).contains("Customer cancellation");
        }

        @Test
        @DisplayName("Should reject refund from INITIATED")
        void shouldRejectRefundFromInitiated() {
            samplePayment.setStatus(PaymentStatus.INITIATED);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            assertThatThrownBy(() -> paymentService.refundPayment("PAY-000001", null))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }

        @Test
        @DisplayName("Should reject refund from PROCESSING")
        void shouldRejectRefundFromProcessing() {
            samplePayment.setStatus(PaymentStatus.PROCESSING);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            assertThatThrownBy(() -> paymentService.refundPayment("PAY-000001", null))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }

        @Test
        @DisplayName("Should reject refund from FAILED")
        void shouldRejectRefundFromFailed() {
            samplePayment.setStatus(PaymentStatus.FAILED);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            assertThatThrownBy(() -> paymentService.refundPayment("PAY-000001", null))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }

        @Test
        @DisplayName("Should reject refund from REFUNDED (terminal)")
        void shouldRejectRefundFromRefunded() {
            samplePayment.setStatus(PaymentStatus.REFUNDED);
            when(paymentRepository.findByPaymentId("PAY-000001")).thenReturn(Optional.of(samplePayment));

            assertThatThrownBy(() -> paymentService.refundPayment("PAY-000001", null))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }
    }

    @Nested
    @DisplayName("List Payments")
    class ListPaymentsTests {

        @Test
        @DisplayName("Should list payments with pagination")
        void shouldListPaymentsWithPagination() {
            Page<Payment> page = new PageImpl<>(List.of(samplePayment));
            when(paymentRepository.findAll(any(Pageable.class))).thenReturn(page);

            PageResponse<PaymentResponse> response = paymentService.listPayments(
                    null, null, null, 0, 10, "createdAt", "desc");

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should list payments filtered by customerId")
        void shouldListPaymentsFilteredByCustomerId() {
            Page<Payment> page = new PageImpl<>(List.of(samplePayment));
            when(paymentRepository.findByCustomerId(eq("CUST-001"), any(Pageable.class))).thenReturn(page);

            PageResponse<PaymentResponse> response = paymentService.listPayments(
                    "CUST-001", null, null, 0, 10, "createdAt", "desc");

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should list payments filtered by status")
        void shouldListPaymentsFilteredByStatus() {
            Page<Payment> page = new PageImpl<>(List.of(samplePayment));
            when(paymentRepository.findByStatus(eq(PaymentStatus.INITIATED), any(Pageable.class))).thenReturn(page);

            PageResponse<PaymentResponse> response = paymentService.listPayments(
                    null, null, PaymentStatus.INITIATED, 0, 10, "createdAt", "desc");

            assertThat(response.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Get Payments by Order ID")
    class GetPaymentsByOrderIdTests {

        @Test
        @DisplayName("Should get payments by order ID")
        void shouldGetPaymentsByOrderId() {
            when(paymentRepository.findByOrderId("ORD-1001")).thenReturn(List.of(samplePayment));

            List<PaymentResponse> response = paymentService.getPaymentsByOrderId("ORD-1001");

            assertThat(response).hasSize(1);
            assertThat(response.get(0).getOrderId()).isEqualTo("ORD-1001");
        }

        @Test
        @DisplayName("Should get latest payment by order ID")
        void shouldGetLatestPaymentByOrderId() {
            when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc("ORD-1001"))
                    .thenReturn(Optional.of(samplePayment));

            PaymentResponse response = paymentService.getLatestPaymentByOrderId("ORD-1001");

            assertThat(response.getOrderId()).isEqualTo("ORD-1001");
        }

        @Test
        @DisplayName("Should throw when no payment found for order")
        void shouldThrowWhenNoPaymentFoundForOrder() {
            when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc("ORD-9999"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getLatestPaymentByOrderId("ORD-9999"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ORD-9999");
        }
    }
}
