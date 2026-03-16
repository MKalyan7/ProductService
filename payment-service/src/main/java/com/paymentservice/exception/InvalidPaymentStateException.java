package com.paymentservice.exception;

import lombok.Getter;

@Getter
public class InvalidPaymentStateException extends BusinessException {

    public InvalidPaymentStateException(String message) {
        super(ErrorCode.INVALID_PAYMENT_STATE, message);
    }
}
