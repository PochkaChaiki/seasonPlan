package com.seasonplan.security.api;

import com.seasonplan.security.exception.UserAdministrationException;
import com.seasonplan.security.exception.AccessDeniedForRoleException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class UserAdministrationExceptionHandler {
    @ExceptionHandler(AccessDeniedForRoleException.class)
    ResponseEntity<Map<String, Object>> forbidden(AccessDeniedForRoleException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(UserAdministrationException.class)
    ResponseEntity<Map<String, String>> handle(UserAdministrationException error) {
        HttpStatus status = switch (error.kind()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case DUPLICATE_USERNAME -> HttpStatus.CONFLICT;
            case INVALID_CURRENT_PASSWORD -> HttpStatus.BAD_REQUEST;
        };
        String code = switch (error.kind()) {
            case VALIDATION -> "VALIDATION_FAILED";
            case DUPLICATE_USERNAME -> "USERNAME_CONFLICT";
            case INVALID_CURRENT_PASSWORD -> "INVALID_CURRENT_PASSWORD";
        };
        return ResponseEntity.status(status).body(Map.of("code", code, "message", error.getMessage()));
    }
}
