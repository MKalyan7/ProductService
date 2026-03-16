package com.paymentservice.seed;

import com.paymentservice.entity.Payment;
import com.paymentservice.entity.PaymentMethod;
import com.paymentservice.entity.PaymentStatus;
import com.paymentservice.entity.SeedRun;
import com.paymentservice.repository.PaymentRepository;
import com.paymentservice.repository.SeedRunRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSeedService {

    private final PaymentRepository paymentRepository;
    private final SeedRunRepository seedRunRepository;

    private static final String SEED_DATA_TAG = "SEED";

    private static final String[] CUSTOMER_IDS = {
            "CUST-001", "CUST-002", "CUST-003", "CUST-004", "CUST-005",
            "CUST-006", "CUST-007", "CUST-008", "CUST-009", "CUST-010"
    };

    private static final String[] ORDER_IDS = {
            "ORD-1001", "ORD-1002", "ORD-1003", "ORD-1004", "ORD-1005",
            "ORD-1006", "ORD-1007", "ORD-1008", "ORD-1009", "ORD-1010",
            "ORD-1011", "ORD-1012", "ORD-1013", "ORD-1014", "ORD-1015",
            "ORD-1016", "ORD-1017", "ORD-1018", "ORD-1019", "ORD-1020"
    };

    private static final PaymentMethod[] METHODS = PaymentMethod.values();
    private static final PaymentStatus[] STATUSES = PaymentStatus.values();

    public SeedRun seed(int count, long seedValue, boolean reset) {
        long startTime = System.currentTimeMillis();
        log.info("Starting payment seed: count={}, seed={}, reset={}", count, seedValue, reset);

        try {
            if (reset) {
                long deleted = paymentRepository.countByDataTag(SEED_DATA_TAG);
                paymentRepository.deleteByDataTag(SEED_DATA_TAG);
                log.info("Deleted {} existing seeded payments", deleted);
            }

            Random random = new Random(seedValue);
            List<Payment> payments = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                String orderId = ORDER_IDS[random.nextInt(ORDER_IDS.length)];
                String customerId = CUSTOMER_IDS[random.nextInt(CUSTOMER_IDS.length)];
                PaymentMethod method = METHODS[random.nextInt(METHODS.length)];
                PaymentStatus status = STATUSES[random.nextInt(STATUSES.length)];
                BigDecimal amount = BigDecimal.valueOf(10 + random.nextDouble() * 990)
                        .setScale(2, RoundingMode.HALF_UP);

                String paymentId = String.format("PAY-SEED-%06d", i + 1);

                Payment payment = Payment.builder()
                        .paymentId(paymentId)
                        .orderId(orderId)
                        .customerId(customerId)
                        .amount(amount)
                        .currency("USD")
                        .method(method)
                        .status(status)
                        .dataTag(SEED_DATA_TAG)
                        .build();

                if (status == PaymentStatus.SUCCESS) {
                    payment.setProviderReference("TXN-" + String.format("%08d", random.nextInt(99999999)));
                } else if (status == PaymentStatus.FAILED) {
                    String[] reasons = {"Card declined", "Insufficient funds", "Network timeout", "Bank rejected"};
                    payment.setFailureReason(reasons[random.nextInt(reasons.length)]);
                } else if (status == PaymentStatus.REFUNDED) {
                    payment.setProviderReference("TXN-" + String.format("%08d", random.nextInt(99999999)));
                    payment.setFailureReason("Refund: Customer requested");
                }

                payments.add(payment);
            }

            paymentRepository.saveAll(payments);
            long durationMs = System.currentTimeMillis() - startTime;

            SeedRun seedRun = SeedRun.builder()
                    .status("COMPLETED")
                    .recordsCreated(count)
                    .durationMs(durationMs)
                    .seed(seedValue)
                    .build();

            seedRunRepository.save(seedRun);
            log.info("Payment seed completed: {} records in {}ms", count, durationMs);
            return seedRun;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Payment seed failed: {}", e.getMessage(), e);

            SeedRun seedRun = SeedRun.builder()
                    .status("FAILED")
                    .recordsCreated(0)
                    .durationMs(durationMs)
                    .seed(seedValue)
                    .errorMessage(e.getMessage())
                    .build();

            seedRunRepository.save(seedRun);
            return seedRun;
        }
    }

    public SeedStatus getSeedStatus() {
        long totalPayments = paymentRepository.count();
        long seededPayments = paymentRepository.countByDataTag(SEED_DATA_TAG);
        SeedRun lastRun = seedRunRepository.findTopByOrderByCreatedAtDesc().orElse(null);
        return new SeedStatus(totalPayments, seededPayments, lastRun);
    }

    @Data
    @AllArgsConstructor
    public static class SeedStatus {
        private long totalPayments;
        private long seededPayments;
        private SeedRun lastRun;
    }
}
