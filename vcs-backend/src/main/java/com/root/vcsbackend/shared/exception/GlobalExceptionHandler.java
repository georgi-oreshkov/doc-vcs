package com.root.vcsbackend.shared.exception;

import com.root.vcsbackend.model.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        log.warn("Application error: {} (HTTP {})", ex.getMessage(), ex.getStatus().value());
        ErrorResponse body = new ErrorResponse(ex.getStatus().value(), ex.getMessage());
        body.setTimestamp(OffsetDateTime.now());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /** Handles @Valid / @Validated bean-level constraint failures. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", message);
        ErrorResponse body = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message);
        body.setTimestamp(OffsetDateTime.now());
        return ResponseEntity.badRequest().body(body);
    }

    /** Catch-all for unexpected exceptions — avoids leaking stack traces. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorResponse body = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred"
        );
        body.setTimestamp(OffsetDateTime.now());
        return ResponseEntity.internalServerError().body(body);
    }
}
