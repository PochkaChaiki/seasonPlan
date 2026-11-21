package com.seasonplan.security.service;

import com.seasonplan.security.exception.AccessDeniedForRoleException;
import com.seasonplan.security.exception.UserAdministrationException;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.security.identity.CurrentActor;
import com.seasonplan.security.repository.UserAccountRepository;
import com.seasonplan.security.service.AccountTransaction;
import com.seasonplan.security.service.LocalUserAdministration.CredentialChangeCommand;
import com.seasonplan.security.service.LocalUserAdministration.CreateUserCommand;
import com.seasonplan.security.service.LocalUserAdministrationService;
import com.seasonplan.security.service.PasswordHasher;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LocalUserAdministrationServiceTest {
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final AccountTransaction transaction = new AccountTransaction() {
        public <T> T execute(Supplier<T> action) { return action.get(); }
    };
    private final LocalUserAdministrationService service = new LocalUserAdministrationService(accounts, passwords, transaction);
    private final CurrentActor actor = new CurrentActor(UUID.randomUUID(), "user", Set.of());

    @Test void existingSystemAdministratorMakesBootstrapInitialOnly() {
        when(accounts.hasSystemAdministrator()).thenReturn(true);
        service.createInitialSystemAdministrator("dev-admin", "Developer", "long-safe-password");
        verify(accounts, never()).insert(any(), any(), any(), anyBoolean(), any(), any());
        verifyNoInteractions(passwords);
    }

    @Test void bootstrapCreatesAdministratorWithHashAndNoBusinessRoles() {
        when(passwords.encode("long-safe-password")).thenReturn("{bcrypt}encoded");
        service.createInitialSystemAdministrator(" Dev-Admin ", "Developer", "long-safe-password");
        verify(accounts).insert(any(), eq("dev-admin"), eq("Developer"), eq(true), eq(Set.of()), eq("{bcrypt}encoded"));
    }

    @Test void nonAdminCannotCreateUserEvenWithBusinessRoles() {
        var command = new CreateUserCommand("user", "User", "long-safe-password", Set.of(ActorRole.CHIEF_GUARDIAN));
        assertThatThrownBy(() -> service.createUser(actor, command)).isInstanceOf(AccessDeniedForRoleException.class);
        verifyNoInteractions(accounts, passwords);
    }

    @Test void administratorCreatesNonAdminUserWithNormalizedDataAndHash() {
        var admin = new CurrentActor(UUID.randomUUID(), "admin", Set.of(), true);
        when(passwords.encode("long-safe-password")).thenReturn("{bcrypt}encoded");
        var result = service.createUser(admin, new CreateUserCommand(" User ", " User ", "long-safe-password", Set.of(ActorRole.FAIRY_PLANNER)));
        verify(accounts).insert(eq(result.id()), eq("user"), eq("User"), eq(false), eq(Set.of(ActorRole.FAIRY_PLANNER)), eq("{bcrypt}encoded"));
    }

    @Test void staleSessionPasswordCannotAuthorizeCredentialChange() {
        when(accounts.lockCredentialHash(actor.userId())).thenReturn(Optional.of("{bcrypt}current"));
        when(passwords.matches("old-password", "{bcrypt}current")).thenReturn(false);
        assertThatThrownBy(() -> service.updateOwnCredentials(actor, new CredentialChangeCommand("old-password", "new-user", "new-long-password")))
                .isInstanceOf(UserAdministrationException.class).hasMessageContaining("Current password");
        verify(accounts, never()).updateCredentials(any(), any(), any());
        verify(passwords, never()).encode(any());
    }

    @Test void missingCredentialOrCurrentPasswordCannotWrite() {
        when(accounts.lockCredentialHash(actor.userId())).thenReturn(Optional.empty(), Optional.of("hash"));
        for (String password : new String[]{"old-password", ""}) {
            assertThatThrownBy(() -> service.updateOwnCredentials(actor, new CredentialChangeCommand(password, "user", "new-long-password")))
                    .isInstanceOf(UserAdministrationException.class).hasMessageContaining("Current password");
        }
        verify(accounts, never()).updateCredentials(any(), any(), any());
        verifyNoInteractions(passwords);
    }

    @Test void credentialChangeUsesCurrentHashAndAuthenticatedId() {
        when(accounts.lockCredentialHash(actor.userId())).thenReturn(Optional.of("{bcrypt}current"));
        when(passwords.matches("current-password", "{bcrypt}current")).thenReturn(true);
        when(passwords.encode("new-long-password")).thenReturn("{bcrypt}new");
        service.updateOwnCredentials(actor, new CredentialChangeCommand("current-password", " New-User ", "new-long-password"));
        verify(accounts).updateCredentials(actor.userId(), "new-user", "{bcrypt}new");
    }
}
