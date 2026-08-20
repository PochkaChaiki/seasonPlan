package com.seasonplan.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Creates a local administrator only for explicitly selected development environments. */
@Component
@Profile("dev")
public class DevelopmentAdministratorBootstrap implements ApplicationRunner {
    private final LocalUserAdministration users;
    private final String username;
    private final String password;
    private final String displayName;

    public DevelopmentAdministratorBootstrap(LocalUserAdministration users,
                                             @Value("${DEV_ADMIN_USERNAME:}") String username,
                                             @Value("${DEV_ADMIN_PASSWORD:}") String password,
                                             @Value("${DEV_ADMIN_DISPLAY_NAME:Development Administrator}") String displayName) {
        this.users = users;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("DEV_ADMIN_USERNAME and DEV_ADMIN_PASSWORD must be configured when the dev profile is active");
        }
        users.createInitialSystemAdministrator(username, displayName.isBlank() ? "Development Administrator" : displayName, password);
    }
}
