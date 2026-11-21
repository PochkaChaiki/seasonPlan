package com.seasonplan.security.service;

import com.seasonplan.security.domain.ManagedUser;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.security.identity.CurrentActor;
import java.util.Set;

/** Application boundary for local account provisioning and self-service credential changes. */
public interface LocalUserAdministration {
    ManagedUser createUser(CurrentActor actor, CreateUserCommand command);
    void updateOwnCredentials(CurrentActor actor, CredentialChangeCommand command);
    void createInitialSystemAdministrator(String username, String displayName, String password);

    record CreateUserCommand(String username, String displayName, String password, Set<ActorRole> roles) {
        public CreateUserCommand {
            username = AccountValidation.username(username);
            displayName = AccountValidation.displayName(displayName);
            password = AccountValidation.password(password);
            roles = AccountValidation.roles(roles);
        }
    }

    record CredentialChangeCommand(String currentPassword, String username, String newPassword) {
        public CredentialChangeCommand {
            username = AccountValidation.username(username);
            newPassword = AccountValidation.password(newPassword);
        }
    }
}
