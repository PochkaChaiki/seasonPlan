package com.seasonplan.lifecycle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.security.JsonAuthenticationHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        csrf.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return http
                .csrf(configurer -> configurer.csrfTokenRepository(csrf))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/users").hasRole("SYSTEM_ADMIN")
                        .requestMatchers("/admin/**").hasRole("SYSTEM_ADMIN")
                        .requestMatchers("/", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").denyAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login.loginProcessingUrl("/auth/login")
                        .successHandler(JsonAuthenticationHandlers.success(objectMapper))
                        .failureHandler(JsonAuthenticationHandlers.failure(objectMapper))
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .deleteCookies("JSESSIONID", "SESSION", "XSRF-TOKEN"))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> JsonAuthenticationHandlers.write(response, 401, objectMapper,
                                java.util.Map.of("code", "UNAUTHENTICATED", "message", "Authentication is required")))
                        .accessDeniedHandler((request, response, exception) -> JsonAuthenticationHandlers.write(response, 403, objectMapper,
                                java.util.Map.of("code", (exception instanceof InvalidCsrfTokenException || exception instanceof MissingCsrfTokenException) ? "CSRF_INVALID" : "ACCESS_DENIED",
                                        "message", "Access is denied"))))
                .httpBasic(httpBasic -> httpBasic.disable())
                .rememberMe(rememberMe -> rememberMe.disable())
                .build();
    }
}
