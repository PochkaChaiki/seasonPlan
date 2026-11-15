package com.seasonplan.security.domain;

import com.seasonplan.security.identity.ActorRole;

import java.util.Set;
import java.util.UUID;

public record ManagedUser(UUID id, String username, String displayName, boolean enabled, Set<ActorRole> roles) {
    public ManagedUser {
        roles = Set.copyOf(roles);
    }
}
