package com.paymentservice.repository;

import com.paymentservice.entity.Payment;
import com.paymentservice.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByPaymentId(String paymentId);

    boolean existsByPaymentId(String paymentId);

    List<Payment> findByOrderId(String orderId);

    Page<Payment> findByCustomerId(String customerId, Pageable pageable);

    Page<Payment> findByOrderId(String orderId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByCustomerIdAndStatus(String customerId, PaymentStatus status, Pageable pageable);

    Page<Payment> findByOrderIdAndStatus(String orderId, PaymentStatus status, Pageable pageable);

    Page<Payment> findByCustomerIdAndOrderId(String customerId, String orderId, Pageable pageable);

    Page<Payment> findByCustomerIdAndOrderIdAndStatus(String customerId, String orderId,
                                                       PaymentStatus status, Pageable pageable);

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(String orderId);

    long countByDataTag(String dataTag);

    void deleteByDataTag(String dataTag);
}
