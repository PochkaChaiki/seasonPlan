package com.seasonplan.security;

import com.seasonplan.lifecycle.domain.ActorRole;

import java.util.Set;
import java.util.UUID;

public record ManagedUser(UUID id, String username, String displayName, boolean enabled, Set<ActorRole> roles) {
    public ManagedUser {
        roles = Set.copyOf(roles);
    }
}
