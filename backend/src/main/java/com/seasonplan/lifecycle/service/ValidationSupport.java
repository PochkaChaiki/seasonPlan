package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.DomainValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ValidationSupport {
    private ValidationSupport() {
    }

    public static void add(Map<String, List<String>> errors, String field, String message) {
        errors.computeIfAbsent(field, ignored -> new ArrayList<>()).add(message);
    }

    public static void throwIfAny(LinkedHashMap<String, List<String>> errors) {
        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }
}
