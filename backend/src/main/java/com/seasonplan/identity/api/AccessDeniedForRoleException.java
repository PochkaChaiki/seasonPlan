package com.seasonplan.identity.api;

import com.seasonplan.identity.api.ActorRole;

public class AccessDeniedForRoleException extends RuntimeException {
    public AccessDeniedForRoleException(String message) {
        super(message);
    }

    public AccessDeniedForRoleException(ActorRole role) {
        super("role is not allowed for this command: " + role);
    }
}
