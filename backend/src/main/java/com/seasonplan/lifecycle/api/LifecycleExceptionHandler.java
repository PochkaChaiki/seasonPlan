package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.application.shared.AccessDeniedForRoleException;
import com.seasonplan.lifecycle.domain.DomainValidationException;
import com.seasonplan.lifecycle.application.shared.NotFoundException;
import com.seasonplan.lifecycle.application.shared.PollenTreeUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(PollenTreeUnavailableException.class)
    ResponseEntity<Map<String, Object>> pollenTreeUnavailable(PollenTreeUnavailableException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "Pollen Tree is unavailable"));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", error.getMessage()));
    }

}
