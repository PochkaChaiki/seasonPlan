package com.seasonplan.security.repository;

import com.seasonplan.security.identity.ActorRole;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Account storage participating in the enclosing AccountTransaction; credentials are encoded hashes only. */
public interface UserAccountRepository {
    boolean hasSystemAdministrator();
    void insert(UUID id, String username, String displayName, boolean systemAdministrator, Set<ActorRole> roles, String encodedHash);
    /** Returns the current hash, holding its account lock until transaction completion; empty means no credential. */
    Optional<String> lockCredentialHash(UUID userId);
    void updateCredentials(UUID userId, String username, String encodedHash);
}
