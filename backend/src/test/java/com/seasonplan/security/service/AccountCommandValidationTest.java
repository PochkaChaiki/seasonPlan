package com.seasonplan.security.service;

import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.identity.api.UserAdministrationException;
import com.seasonplan.identity.api.LocalUserAdministration.CreateUserCommand;
import com.seasonplan.identity.api.LocalUserAdministration.CredentialChangeCommand;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class AccountCommandValidationTest {
    @Test void createNormalizesAndCopiesRoles() {
        var roles = new HashSet<>(Set.of(ActorRole.FAIRY_PLANNER));
        var command = new CreateUserCommand(" User-1 ", " Name ", "long-safe-password", roles);
        roles.clear();
        assertThat(command.username()).isEqualTo("user-1");
        assertThat(command.displayName()).isEqualTo("Name");
        assertThat(command.roles()).containsExactly(ActorRole.FAIRY_PLANNER);
        assertThatThrownBy(() -> command.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rejectsInvalidFieldsAndUtf8ByteLength() {
        assertThatThrownBy(() -> new CreateUserCommand("bad space", "Name", "long-safe-password", Set.of(ActorRole.FAIRY_PLANNER)))
                .isInstanceOf(UserAdministrationException.class).hasMessageContaining("Username");
        assertThatThrownBy(() -> new CreateUserCommand("user", "x".repeat(201), "long-safe-password", Set.of(ActorRole.FAIRY_PLANNER)))
                .isInstanceOf(UserAdministrationException.class).hasMessageContaining("Display name");
        assertThatThrownBy(() -> new CreateUserCommand("user", "Name", "short", Set.of(ActorRole.FAIRY_PLANNER)))
                .isInstanceOf(UserAdministrationException.class).hasMessageContaining("Password");
        assertThatThrownBy(() -> new CredentialChangeCommand("current", "user", "é".repeat(37)))
                .isInstanceOf(UserAdministrationException.class).hasMessageContaining("72 UTF-8 bytes");
        assertThatThrownBy(() -> new CreateUserCommand("user", "Name", "long-safe-password", Set.of()))
                .isInstanceOf(UserAdministrationException.class).hasMessageContaining("business role");
    }
}
