package com.seasonplan.security;

import com.seasonplan.lifecycle.domain.ActorRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalUserDetailsTest {
    @Test
    void systemAdministratorAuthorityIsSeparateFromTheSevenBusinessRoles() {
        LocalUserDetails user = new LocalUserDetails(UUID.randomUUID(), "admin", "Admin", "{bcrypt}hash", true, true,
                java.util.Set.of(ActorRole.FAIRY_PLANNER));

        assertThat(user.getAuthorities()).extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("ROLE_SYSTEM_ADMIN", "ROLE_FAIRY_PLANNER");
        assertThat(user.roles()).containsExactly(ActorRole.FAIRY_PLANNER);
    }

    @Test
    void passwordsLongerThanBcyrptInputLimitAreRejectedBeforeEncoding() {
        assertThatThrownBy(() -> LocalUserAdministrationService.validatedPassword("a".repeat(73)))
                .isInstanceOf(UserAdministrationException.class)
                .hasMessageContaining("72 UTF-8 bytes");
    }

    @Test
    void currentActorProviderDoesNotTreatAdministratorAsABusinessRole() {
        LocalUserDetails user = new LocalUserDetails(UUID.randomUUID(), "admin", "Admin", "{bcrypt}hash", true, true,
                java.util.Set.of(ActorRole.FAIRY_PLANNER));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", user.getAuthorities()));
        try {
            assertThat(new SecurityCurrentActorProvider().currentActor().roles()).containsExactly(ActorRole.FAIRY_PLANNER);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
