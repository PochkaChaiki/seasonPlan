package com.seasonplan.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DevelopmentAdministratorBootstrapTest {
    @Test
    void bootstrapIsExplicitlyDevOnlyAndRefusesMissingCredentials() {
        Profile profile = DevelopmentAdministratorBootstrap.class.getAnnotation(Profile.class);
        assertThat(profile.value()).containsExactly("dev");

        LocalUserAdministration users = mock(LocalUserAdministration.class);
        DevelopmentAdministratorBootstrap bootstrap = new DevelopmentAdministratorBootstrap(users, " ", "", "");

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEV_ADMIN_USERNAME");
        verifyNoInteractions(users);
    }

    @Test
    void bootstrapUsesTheDefaultDisplayNameWhenItIsBlank() throws Exception {
        LocalUserAdministration users = mock(LocalUserAdministration.class);
        DevelopmentAdministratorBootstrap bootstrap = new DevelopmentAdministratorBootstrap(users, " Dev-Admin ", "long-safe-password", " ");

        bootstrap.run(new DefaultApplicationArguments());

        verify(users).createInitialSystemAdministrator(" Dev-Admin ", "Development Administrator", "long-safe-password");
    }
}
