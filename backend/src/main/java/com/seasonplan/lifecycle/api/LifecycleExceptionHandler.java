package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.service.AccessDeniedForRoleException;
import com.seasonplan.lifecycle.domain.DomainValidationException;
import com.seasonplan.lifecycle.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class LifecycleExceptionHandler {
    @ExceptionHandler(DomainValidationException.class)
    ResponseEntity<Map<String, Object>> validation(DomainValidationException error) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", error.getMessage(),
                "errors", error.fieldErrors()
        ));
    }

    @ExceptionHandler(AccessDeniedForRoleException.class)
    ResponseEntity<Map<String, Object>> forbidden(AccessDeniedForRoleException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Map<String, Object>> missingHeader(MissingRequestHeaderException error) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "message", "actor headers are required",
                "errors", Map.of(error.getHeaderName(), List.of("header is required"))
        ));
    }
}
