package com.orderservice.exception;

import lombok.Getter;

@Getter
public class InvalidOrderStateException extends BusinessException {

    public InvalidOrderStateException(String message) {
        super(ErrorCode.INVALID_ORDER_STATE, message);
    }
}
