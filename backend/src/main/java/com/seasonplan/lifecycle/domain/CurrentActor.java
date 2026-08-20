package com.seasonplan.lifecycle.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Provider-neutral authenticated application identity. */
public record CurrentActor(UUID userId, String username, Set<ActorRole> roles) {
    public CurrentActor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
        roles = Set.copyOf(roles);
    }

    public ActorRole requiredRole(ActorRole... allowed) {
        for (ActorRole role : allowed) {
            if (roles.contains(role)) {
                return role;
            }
        }
        throw new IllegalStateException("authenticated actor lacks required role");
    }
}
