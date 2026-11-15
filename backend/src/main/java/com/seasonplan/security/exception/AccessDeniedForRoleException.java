package com.seasonplan.security.exception;

import com.seasonplan.security.identity.ActorRole;

public class AccessDeniedForRoleException extends RuntimeException {
    public AccessDeniedForRoleException(String message) {
        super(message);
    }

    public AccessDeniedForRoleException(ActorRole role) {
        super("role is not allowed for this command: " + role);
    }
}
