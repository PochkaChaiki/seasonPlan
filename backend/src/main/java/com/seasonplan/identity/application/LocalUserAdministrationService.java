package com.seasonplan.identity.application;

import com.seasonplan.identity.api.LocalUserAdministration;
import com.seasonplan.identity.application.port.AccountTransaction;
import com.seasonplan.identity.application.port.PasswordHasher;
import com.seasonplan.identity.domain.AccountValidation;
import com.seasonplan.identity.domain.AccountAdministrationPolicy;

import com.seasonplan.identity.domain.ManagedUser;
import com.seasonplan.identity.api.AccessDeniedForRoleException;
import com.seasonplan.identity.api.UserAdministrationException;
import com.seasonplan.identity.api.CurrentActor;
import com.seasonplan.identity.application.port.UserAccountRepository;
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
        AccountAdministrationPolicy.requireAdministrator(actor);
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
            boolean matches = AccountAdministrationPolicy.canVerify(command.currentPassword(), hash)
                    && passwords.matches(command.currentPassword(), hash);
            AccountAdministrationPolicy.requireCurrentPassword(command.currentPassword(), hash, matches);
            accounts.updateCredentials(actor.userId(), command.username(), passwords.encode(command.newPassword()));
            return null;
        });
    }

    @Override
    public void createInitialSystemAdministrator(String username, String displayName, String password) {
        transactions.execute(() -> {
            if (!AccountAdministrationPolicy.bootstrapRequired(accounts.hasSystemAdministrator())) return null;
            String normalizedUsername = AccountValidation.username(username);
            String validDisplayName = AccountValidation.displayName(displayName);
            String validPassword = AccountValidation.password(password);
            accounts.insert(UUID.randomUUID(), normalizedUsername, validDisplayName, true, Set.of(), passwords.encode(validPassword));
            return null;
        });
    }
}
