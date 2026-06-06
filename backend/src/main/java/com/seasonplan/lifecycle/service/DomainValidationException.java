package com.seasonplan.lifecycle.service;

import java.util.List;
import java.util.Map;

public class DomainValidationException extends RuntimeException {
    private final Map<String, List<String>> fieldErrors;

    public DomainValidationException(Map<String, List<String>> fieldErrors) {
        super("domain validation failed");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, List<String>> fieldErrors() {
        return fieldErrors;
    }
}
