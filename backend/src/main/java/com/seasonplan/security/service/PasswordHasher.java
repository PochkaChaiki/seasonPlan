package com.seasonplan.security.service;

public interface PasswordHasher {
    String encode(String password);
    boolean matches(String password, String encodedHash);
}
