package com.seasonplan.identity.domain;

import com.seasonplan.identity.api.ActorRole;

import java.util.Set;
import java.util.UUID;

public record ManagedUser(UUID id, String username, String displayName, boolean enabled, Set<ActorRole> roles) {
    public ManagedUser {
        roles = Set.copyOf(roles);
    }
}
