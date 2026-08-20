package com.seasonplan.security;

/** Application boundary for local account provisioning and self-service credential changes. */
public interface LocalUserAdministration {
    ManagedUser createUser(LocalUserAdministrationService.CreateUserCommand command);
    void updateOwnCredentials(LocalUserDetails user, LocalUserAdministrationService.CredentialChangeCommand command);
    void createInitialSystemAdministrator(String username, String displayName, String password);
}
