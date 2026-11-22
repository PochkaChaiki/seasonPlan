package com.seasonplan.workflows.adapter.in.web;

import com.seasonplan.shared.domain.DomainValidationException;
import com.seasonplan.shared.application.NotFoundException;
import com.seasonplan.pollen.api.PollenTreeUnavailableException;
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

    @ExceptionHandler(com.seasonplan.pollen.api.PollenCommandPendingException.class)
    ResponseEntity<Map<String, Object>> pollenPending(com.seasonplan.pollen.api.PollenCommandPendingException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", error.getMessage(),
                "code", "POLLEN_OPERATION_PENDING", "operationId", error.operationId().toString()));
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
