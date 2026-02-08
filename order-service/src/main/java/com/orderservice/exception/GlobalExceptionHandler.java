package com.orderservice.exception;

import com.orderservice.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .errorCode(ex.getErrorCode().name())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderStateException(
            InvalidOrderStateException ex, HttpServletRequest request) {
        log.warn("Invalid order state: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .errorCode(ex.getErrorCode().name())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(ProductServiceException.class)
    public ResponseEntity<ErrorResponse> handleProductServiceException(
            ProductServiceException ex, HttpServletRequest request) {
        log.warn("Product service error: {}", ex.getMessage());

        HttpStatus status = resolveProductServiceStatus(ex.getErrorCode());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .errorCode(ex.getErrorCode().name())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .errorCode(ex.getErrorCode().name())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());

        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .errorCode(ErrorCode.VALIDATION_ERROR.name())
                .message("Validation failed")
                .details(details)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .errorCode(ErrorCode.INTERNAL_ERROR.name())
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private HttpStatus resolveProductServiceStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case PRODUCT_SERVICE_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
            case PRODUCT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PRODUCT_INACTIVE, OUT_OF_STOCK -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
