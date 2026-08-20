package com.seasonplan.security;

import java.util.Locale;
import java.util.regex.Pattern;

public final class UsernameNormalizer {
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,99}");
    private UsernameNormalizer() { }
    public static String normalize(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(normalized).matches()) throw new IllegalArgumentException("invalid username");
        return normalized;
    }
}
