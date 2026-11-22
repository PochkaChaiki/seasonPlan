package com.seasonplan.identity.application.port;

public interface PasswordHasher {
    String encode(String password);
    boolean matches(String password, String encodedHash);
}
