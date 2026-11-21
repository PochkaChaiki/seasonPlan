package com.seasonplan.security.identity;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Provider-neutral authenticated application identity. */
public record CurrentActor(UUID userId, String username, Set<ActorRole> roles, boolean systemAdministrator) {
    public CurrentActor(UUID userId, String username, Set<ActorRole> roles) {
        this(userId, username, roles, false);
    }
    public CurrentActor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
        roles = Set.copyOf(roles);
    }

}
