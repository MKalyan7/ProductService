package com.productservice.exception;

import lombok.Getter;

@Getter
public class ConflictException extends BusinessException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
