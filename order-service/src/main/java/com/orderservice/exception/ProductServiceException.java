package com.orderservice.exception;

import lombok.Getter;

@Getter
public class ProductServiceException extends BusinessException {

    public ProductServiceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
