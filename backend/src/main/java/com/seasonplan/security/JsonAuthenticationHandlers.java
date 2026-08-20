package com.seasonplan.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Map;

public final class JsonAuthenticationHandlers {
    private JsonAuthenticationHandlers() { }
    public static AuthenticationSuccessHandler success(ObjectMapper mapper) {
        return (request, response, authentication) -> {
            LocalUserDetails user = (LocalUserDetails) authentication.getPrincipal();
            write(response, 200, mapper, Map.of("id", user.userId(), "username", user.username(),
                    "displayName", user.displayName(), "roles", user.roles(), "administrator", user.systemAdministrator()));
        };
    }
    public static AuthenticationFailureHandler failure(ObjectMapper mapper) {
        return (request, response, exception) -> write(response, 401, mapper,
                Map.of("code", "INVALID_CREDENTIALS", "message", "Invalid credentials"));
    }
    public static void write(HttpServletResponse response, int status, ObjectMapper mapper, Object value) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), value);
    }
}
