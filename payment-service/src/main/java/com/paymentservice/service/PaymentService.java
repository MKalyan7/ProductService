package com.paymentservice.service;

import com.paymentservice.dto.request.CreatePaymentRequest;
import com.paymentservice.dto.request.PaymentFailureRequest;
import com.paymentservice.dto.request.PaymentRefundRequest;
import com.paymentservice.dto.request.PaymentSuccessRequest;
import com.paymentservice.dto.response.PageResponse;
import com.paymentservice.dto.response.PaymentResponse;
import com.paymentservice.entity.Payment;
import com.paymentservice.entity.PaymentStatus;
import com.paymentservice.exception.ErrorCode;
import com.paymentservice.exception.InvalidPaymentStateException;
import com.paymentservice.exception.ResourceNotFoundException;
import com.paymentservice.repository.PaymentRepository;
import com.paymentservice.util.PaymentIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private static final Set<PaymentStatus> PROCESSABLE_STATUSES = Set.of(PaymentStatus.INITIATED);
    private static final Set<PaymentStatus> SUCCESS_ALLOWED_STATUSES = Set.of(PaymentStatus.INITIATED, PaymentStatus.PROCESSING);
    private static final Set<PaymentStatus> FAIL_ALLOWED_STATUSES = Set.of(PaymentStatus.INITIATED, PaymentStatus.PROCESSING);
    private static final Set<PaymentStatus> REFUND_ALLOWED_STATUSES = Set.of(PaymentStatus.SUCCESS);

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("Creating payment for order: {}, customer: {}", request.getOrderId(), request.getCustomerId());

        String paymentId = generateUniquePaymentId();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .method(request.getMethod())
                .status(PaymentStatus.INITIATED)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created: {} for order: {}", paymentId, request.getOrderId());

        return mapToResponse(savedPayment);
    }

    public PaymentResponse getPaymentById(String paymentId) {
        log.info("Fetching payment: {}", paymentId);
        Payment payment = findPaymentByPaymentId(paymentId);
        return mapToResponse(payment);
    }

    public PageResponse<PaymentResponse> listPayments(String customerId, String orderId,
                                                       PaymentStatus status, int page, int size,
                                                       String sortBy, String sortDir) {
        log.info("Listing payments - customerId: {}, orderId: {}, status: {}, page: {}, size: {}",
                customerId, orderId, status, page, size);

        Sort sort = sortDir != null && sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy != null ? sortBy : "createdAt").ascending()
                : Sort.by(sortBy != null ? sortBy : "createdAt").descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Payment> paymentPage = findPaymentsWithFilters(customerId, orderId, status, pageable);

        return PageResponse.<PaymentResponse>builder()
                .content(paymentPage.getContent().stream().map(this::mapToResponse).toList())
                .page(paymentPage.getNumber())
                .size(paymentPage.getSize())
                .totalElements(paymentPage.getTotalElements())
                .totalPages(paymentPage.getTotalPages())
                .first(paymentPage.isFirst())
                .last(paymentPage.isLast())
                .build();
    }

    public PaymentResponse processPayment(String paymentId) {
        log.info("Processing payment: {}", paymentId);
        Payment payment = findPaymentByPaymentId(paymentId);

        validateStateTransition(payment, PROCESSABLE_STATUSES, PaymentStatus.PROCESSING);

        payment.setStatus(PaymentStatus.PROCESSING);
        Payment savedPayment = paymentRepository.save(payment);

        log.info("Payment {} moved to PROCESSING", paymentId);
        return mapToResponse(savedPayment);
    }

    public PaymentResponse markPaymentSuccess(String paymentId, PaymentSuccessRequest request) {
        log.info("Marking payment success: {}", paymentId);
        Payment payment = findPaymentByPaymentId(paymentId);

        validateStateTransition(payment, SUCCESS_ALLOWED_STATUSES, PaymentStatus.SUCCESS);

        payment.setStatus(PaymentStatus.SUCCESS);
        if (request != null && request.getProviderReference() != null) {
            payment.setProviderReference(request.getProviderReference());
        }

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment {} marked as SUCCESS", paymentId);

        // Future: publish PAYMENT_SUCCESS event for order confirmation
        return mapToResponse(savedPayment);
    }

    public PaymentResponse markPaymentFailed(String paymentId, PaymentFailureRequest request) {
        log.info("Marking payment failed: {}", paymentId);
        Payment payment = findPaymentByPaymentId(paymentId);

        validateStateTransition(payment, FAIL_ALLOWED_STATUSES, PaymentStatus.FAILED);

        payment.setStatus(PaymentStatus.FAILED);
        if (request != null && request.getFailureReason() != null) {
            payment.setFailureReason(request.getFailureReason());
        }

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment {} marked as FAILED", paymentId);

        // Future: publish PAYMENT_FAILED event
        return mapToResponse(savedPayment);
    }

    public PaymentResponse refundPayment(String paymentId, PaymentRefundRequest request) {
        log.info("Refunding payment: {}", paymentId);
        Payment payment = findPaymentByPaymentId(paymentId);

        validateStateTransition(payment, REFUND_ALLOWED_STATUSES, PaymentStatus.REFUNDED);

        payment.setStatus(PaymentStatus.REFUNDED);
        if (request != null && request.getReason() != null) {
            payment.setFailureReason("Refund: " + request.getReason());
        }

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment {} marked as REFUNDED", paymentId);

        // Future: publish PAYMENT_REFUNDED event
        return mapToResponse(savedPayment);
    }

    public List<PaymentResponse> getPaymentsByOrderId(String orderId) {
        log.info("Fetching payments for order: {}", orderId);
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        return payments.stream().map(this::mapToResponse).toList();
    }

    public PaymentResponse getLatestPaymentByOrderId(String orderId) {
        log.info("Fetching latest payment for order: {}", orderId);
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYMENT_NOT_FOUND,
                        "No payment found for order: " + orderId));
        return mapToResponse(payment);
    }

    private Payment findPaymentByPaymentId(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Payment not found with ID: " + paymentId));
    }

    private void validateStateTransition(Payment payment, Set<PaymentStatus> allowedFromStatuses,
                                          PaymentStatus targetStatus) {
        if (!allowedFromStatuses.contains(payment.getStatus())) {
            throw new InvalidPaymentStateException(
                    "Cannot transition payment " + payment.getPaymentId() +
                    " from " + payment.getStatus() + " to " + targetStatus);
        }
    }

    private String generateUniquePaymentId() {
        String paymentId;
        int attempts = 0;
        do {
            paymentId = PaymentIdGenerator.generate();
            attempts++;
            if (attempts > 10) {
                throw new RuntimeException("Unable to generate unique payment ID after 10 attempts");
            }
        } while (paymentRepository.existsByPaymentId(paymentId));
        return paymentId;
    }

    private Page<Payment> findPaymentsWithFilters(String customerId, String orderId,
                                                    PaymentStatus status, Pageable pageable) {
        boolean hasCustomer = customerId != null && !customerId.isBlank();
        boolean hasOrder = orderId != null && !orderId.isBlank();
        boolean hasStatus = status != null;

        if (hasCustomer && hasOrder && hasStatus) {
            return paymentRepository.findByCustomerIdAndOrderIdAndStatus(customerId, orderId, status, pageable);
        } else if (hasCustomer && hasOrder) {
            return paymentRepository.findByCustomerIdAndOrderId(customerId, orderId, pageable);
        } else if (hasCustomer && hasStatus) {
            return paymentRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        } else if (hasOrder && hasStatus) {
            return paymentRepository.findByOrderIdAndStatus(orderId, status, pageable);
        } else if (hasCustomer) {
            return paymentRepository.findByCustomerId(customerId, pageable);
        } else if (hasOrder) {
            return paymentRepository.findByOrderId(orderId, pageable);
        } else if (hasStatus) {
            return paymentRepository.findByStatus(status, pageable);
        } else {
            return paymentRepository.findAll(pageable);
        }
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .method(payment.getMethod().name())
                .status(payment.getStatus().name())
                .providerReference(payment.getProviderReference())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
