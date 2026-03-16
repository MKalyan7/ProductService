package com.paymentservice.util;

import java.util.concurrent.atomic.AtomicLong;

public final class PaymentIdGenerator {

    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis() % 100000);

    private PaymentIdGenerator() {
    }

    public static String generate() {
        long next = COUNTER.incrementAndGet();
        return String.format("PAY-%06d", next);
    }
}
