package com.seasonplan.security.service;

import com.seasonplan.security.domain.UsernameNormalizer;
import com.seasonplan.security.exception.UserAdministrationException;
import com.seasonplan.security.identity.ActorRole;
import java.nio.charset.StandardCharsets;
import java.util.Set;

final class AccountValidation {
    private AccountValidation() { }

    static String username(String username) {
        try {
            return UsernameNormalizer.normalize(username);
        } catch (IllegalArgumentException error) {
            throw invalid("Username must contain 1 to 100 lowercase letters, digits, dots, underscores, or hyphens");
        }
    }

    static String displayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 200) {
            throw invalid("Display name is required and must be at most 200 characters");
        }
        return displayName.trim();
    }

    static String password(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalid("Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
        return password;
    }

    static Set<ActorRole> roles(Set<ActorRole> roles) {
        if (roles == null || roles.isEmpty()) throw invalid("At least one business role is required");
        return Set.copyOf(roles);
    }

    private static UserAdministrationException invalid(String message) {
        return new UserAdministrationException(UserAdministrationException.Kind.VALIDATION, message);
    }
}
