package com.paymentservice.repository;

import com.paymentservice.entity.Payment;
import com.paymentservice.entity.PaymentMethod;
import com.paymentservice.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class PaymentRepositoryIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    private Payment createSamplePayment(String paymentId, String orderId, String customerId,
                                         PaymentStatus status, PaymentMethod method) {
        return Payment.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .customerId(customerId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .method(method)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("Should save and find payment by paymentId")
    void shouldSaveAndFindByPaymentId() {
        Payment payment = createSamplePayment("PAY-TEST-001", "ORD-1001", "CUST-001",
                PaymentStatus.INITIATED, PaymentMethod.CREDIT_CARD);
        paymentRepository.save(payment);

        Optional<Payment> found = paymentRepository.findByPaymentId("PAY-TEST-001");

        assertThat(found).isPresent();
        assertThat(found.get().getPaymentId()).isEqualTo("PAY-TEST-001");
        assertThat(found.get().getOrderId()).isEqualTo("ORD-1001");
    }

    @Test
    @DisplayName("Should find payments by orderId")
    void shouldFindByOrderId() {
        paymentRepository.save(createSamplePayment("PAY-001", "ORD-1001", "CUST-001",
                PaymentStatus.INITIATED, PaymentMethod.CREDIT_CARD));
        paymentRepository.save(createSamplePayment("PAY-002", "ORD-1001", "CUST-001",
                PaymentStatus.FAILED, PaymentMethod.DEBIT_CARD));
        paymentRepository.save(createSamplePayment("PAY-003", "ORD-1002", "CUST-002",
                PaymentStatus.SUCCESS, PaymentMethod.UPI));

        List<Payment> payments = paymentRepository.findByOrderId("ORD-1001");

        assertThat(payments).hasSize(2);
    }

    @Test
    @DisplayName("Should find payments by customerId with pagination")
    void shouldFindByCustomerIdWithPagination() {
        for (int i = 0; i < 15; i++) {
            paymentRepository.save(createSamplePayment("PAY-" + i, "ORD-100" + i, "CUST-001",
                    PaymentStatus.INITIATED, PaymentMethod.CREDIT_CARD));
        }

        Page<Payment> page = paymentRepository.findByCustomerId("CUST-001",
                PageRequest.of(0, 10, Sort.by("createdAt").descending()));

        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(15);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should find payments by status")
    void shouldFindByStatus() {
        paymentRepository.save(createSamplePayment("PAY-001", "ORD-1001", "CUST-001",
                PaymentStatus.SUCCESS, PaymentMethod.CREDIT_CARD));
        paymentRepository.save(createSamplePayment("PAY-002", "ORD-1002", "CUST-002",
                PaymentStatus.SUCCESS, PaymentMethod.UPI));
        paymentRepository.save(createSamplePayment("PAY-003", "ORD-1003", "CUST-003",
                PaymentStatus.FAILED, PaymentMethod.NET_BANKING));

        Page<Payment> page = paymentRepository.findByStatus(PaymentStatus.SUCCESS,
                PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Should find latest payment by orderId")
    void shouldFindLatestByOrderId() {
        Payment p1 = createSamplePayment("PAY-001", "ORD-1001", "CUST-001",
                PaymentStatus.FAILED, PaymentMethod.CREDIT_CARD);
        p1.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        paymentRepository.save(p1);

        Payment p2 = createSamplePayment("PAY-002", "ORD-1001", "CUST-001",
                PaymentStatus.SUCCESS, PaymentMethod.DEBIT_CARD);
        p2.setCreatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        paymentRepository.save(p2);

        Optional<Payment> latest = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc("ORD-1001");

        assertThat(latest).isPresent();
        assertThat(latest.get().getPaymentId()).isEqualTo("PAY-002");
    }

    @Test
    @DisplayName("Should check existence by paymentId")
    void shouldCheckExistsByPaymentId() {
        paymentRepository.save(createSamplePayment("PAY-EXISTS", "ORD-1001", "CUST-001",
                PaymentStatus.INITIATED, PaymentMethod.WALLET));

        assertThat(paymentRepository.existsByPaymentId("PAY-EXISTS")).isTrue();
        assertThat(paymentRepository.existsByPaymentId("PAY-NOPE")).isFalse();
    }
}
