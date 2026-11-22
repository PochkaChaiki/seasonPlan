package com.seasonplan.identity.domain;

import com.seasonplan.identity.domain.UsernameNormalizer;
import com.seasonplan.identity.api.UserAdministrationException;
import com.seasonplan.identity.api.ActorRole;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class AccountValidation {
    private AccountValidation() { }

    public static String username(String username) {
        try {
            return UsernameNormalizer.normalize(username);
        } catch (IllegalArgumentException error) {
            throw invalid("Username must contain 1 to 100 lowercase letters, digits, dots, underscores, or hyphens");
        }
    }

    public static String displayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 200) {
            throw invalid("Display name is required and must be at most 200 characters");
        }
        return displayName.trim();
    }

    public static String password(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalid("Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
        return password;
    }

    public static Set<ActorRole> roles(Set<ActorRole> roles) {
        if (roles == null || roles.isEmpty()) throw invalid("At least one business role is required");
        return Set.copyOf(roles);
    }

    private static UserAdministrationException invalid(String message) {
        return new UserAdministrationException(UserAdministrationException.Kind.VALIDATION, message);
    }
}
