package com.seasonplan.security.infrastructure.spring;

import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.identity.adapter.out.spring.LocalUserDetails;
import com.seasonplan.identity.adapter.out.spring.SecurityCurrentActorProvider;
import com.seasonplan.identity.api.LocalUserAdministration;
import com.seasonplan.identity.api.UserAdministrationException;
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
    void missingBusinessRoleUsesDeniedRoleException() {
        var actor = new com.seasonplan.identity.api.CurrentActor(UUID.randomUUID(), "admin", java.util.Set.of());
        assertThatThrownBy(() -> com.seasonplan.identity.api.LifecycleAccessPolicy.roleFor(actor,
                com.seasonplan.identity.api.LifecyclePermission.CREATE_DRAFT))
                .isInstanceOf(com.seasonplan.identity.api.AccessDeniedForRoleException.class);
    }

    @Test
    void passwordsLongerThanBcyrptInputLimitAreRejectedBeforeEncoding() {
        assertThatThrownBy(() -> new LocalUserAdministration.CreateUserCommand("user", "User", "a".repeat(73), java.util.Set.of(ActorRole.FAIRY_PLANNER)))
                .isInstanceOf(UserAdministrationException.class)
                .hasMessageContaining("72 UTF-8 bytes");
    }

    @Test
    void identityAndPrincipalDefensivelyCopyRolesAndPreserveAdministratorFlag() {
        var roles = new java.util.HashSet<>(java.util.Set.of(ActorRole.FAIRY_PLANNER));
        var user = new LocalUserDetails(UUID.randomUUID(), "admin", "Admin", "hash", true, true, roles);
        var actor = new com.seasonplan.identity.api.CurrentActor(user.userId(), user.username(), roles, true);
        roles.clear();
        assertThat(user.roles()).containsExactly(ActorRole.FAIRY_PLANNER);
        assertThat(actor.roles()).containsExactly(ActorRole.FAIRY_PLANNER);
        assertThatThrownBy(() -> user.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> actor.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(actor.systemAdministrator()).isTrue();
        assertThat(new com.seasonplan.identity.api.CurrentActor(user.userId(), user.username(), java.util.Set.of()).systemAdministrator()).isFalse();
    }

    @Test
    void authenticatedPrincipalSurvivesSessionSerialization() throws Exception {
        var user = new LocalUserDetails(UUID.randomUUID(), "admin", "Admin", "hash", true, true, java.util.Set.of(ActorRole.FAIRY_PLANNER));
        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        var buffer = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.ObjectOutputStream(buffer)) { output.writeObject(authentication); }
        try (var input = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(buffer.toByteArray()))) {
            var restored = (UsernamePasswordAuthenticationToken) input.readObject();
            assertThat(restored.getPrincipal()).isEqualTo(user);
            assertThat(restored.getAuthorities()).extracting(authority -> authority.getAuthority())
                    .containsExactlyInAnyOrder("ROLE_SYSTEM_ADMIN", "ROLE_FAIRY_PLANNER");
        }
    }

    @Test
    void currentActorProviderDoesNotTreatAdministratorAsABusinessRole() {
        LocalUserDetails user = new LocalUserDetails(UUID.randomUUID(), "admin", "Admin", "{bcrypt}hash", true, true,
                java.util.Set.of(ActorRole.FAIRY_PLANNER));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", user.getAuthorities()));
        try {
            var actor = new SecurityCurrentActorProvider().currentActor();
            assertThat(actor.roles()).containsExactly(ActorRole.FAIRY_PLANNER);
            assertThat(actor.systemAdministrator()).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
