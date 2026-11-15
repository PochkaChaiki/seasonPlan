package com.seasonplan.security.infrastructure.spring;

import com.seasonplan.security.service.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;

public class SpringPasswordHasher implements PasswordHasher {
    private final PasswordEncoder encoder;
    public SpringPasswordHasher(PasswordEncoder encoder) { this.encoder = encoder; }
    @Override public String encode(String password) { return encoder.encode(password); }
    @Override public boolean matches(String password, String encodedHash) { return encoder.matches(password, encodedHash); }
}
