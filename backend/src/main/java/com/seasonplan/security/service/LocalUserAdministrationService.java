package com.seasonplan.security.service;

import com.seasonplan.security.domain.ManagedUser;
import com.seasonplan.security.exception.AccessDeniedForRoleException;
import com.seasonplan.security.exception.UserAdministrationException;
import com.seasonplan.security.identity.CurrentActor;
import com.seasonplan.security.repository.UserAccountRepository;
import java.util.Set;
import java.util.UUID;

public class LocalUserAdministrationService implements LocalUserAdministration {
    private final UserAccountRepository accounts;
    private final PasswordHasher passwords;
    private final AccountTransaction transactions;

    public LocalUserAdministrationService(UserAccountRepository accounts, PasswordHasher passwords, AccountTransaction transactions) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.transactions = transactions;
    }

    @Override
    public ManagedUser createUser(CurrentActor actor, CreateUserCommand command) {
        if (actor == null || !actor.systemAdministrator()) {
            throw new AccessDeniedForRoleException("system administrator is required");
        }
        return transactions.execute(() -> {
            UUID id = UUID.randomUUID();
            accounts.insert(id, command.username(), command.displayName(), false, command.roles(), passwords.encode(command.password()));
            return new ManagedUser(id, command.username(), command.displayName(), true, command.roles());
        });
    }

    @Override
    public void updateOwnCredentials(CurrentActor actor, CredentialChangeCommand command) {
        transactions.execute(() -> {
            String hash = accounts.lockCredentialHash(actor.userId()).orElse(null);
            if (command.currentPassword() == null || command.currentPassword().isBlank()
                    || hash == null || !passwords.matches(command.currentPassword(), hash)) {
                throw new UserAdministrationException(UserAdministrationException.Kind.INVALID_CURRENT_PASSWORD, "Current password is incorrect");
            }
            accounts.updateCredentials(actor.userId(), command.username(), passwords.encode(command.newPassword()));
            return null;
        });
    }

    @Override
    public void createInitialSystemAdministrator(String username, String displayName, String password) {
        transactions.execute(() -> {
            if (accounts.hasSystemAdministrator()) return null;
            String normalizedUsername = AccountValidation.username(username);
            String validDisplayName = AccountValidation.displayName(displayName);
            String validPassword = AccountValidation.password(password);
            accounts.insert(UUID.randomUUID(), normalizedUsername, validDisplayName, true, Set.of(), passwords.encode(validPassword));
            return null;
        });
    }
}
