package com.seasonplan.lifecycle.application.shared;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.DomainValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ValidationSupport {
    private ValidationSupport() {
    }

    public static void requireRole(ActorRole actual, ActorRole expected) {
        if (actual != expected) {
            throw new AccessDeniedForRoleException(actual);
        }
    }

    public static void requireAnyRole(ActorRole actual, ActorRole... allowed) {
        for (ActorRole role : allowed) {
            if (actual == role) {
                return;
            }
        }
        throw new AccessDeniedForRoleException(actual);
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
