package com.example.dataisolation.api;

import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException ex) { return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict() { return error(HttpStatus.CONFLICT, "DUPLICATE_REQUEST_NO", "Request number already exists for this tenant"); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> invalidBody() { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body contains invalid or unsupported fields"); }
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) { return ResponseEntity.status(status).body(new ApiError(code, message, Instant.now())); }
}
