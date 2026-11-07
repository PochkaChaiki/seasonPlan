package com.seasonplan.lifecycle.application.shared;

import com.seasonplan.lifecycle.domain.ActorRole;

public class AccessDeniedForRoleException extends RuntimeException {
    public AccessDeniedForRoleException(ActorRole role) {
        super("role is not allowed for this command: " + role);
    }
}
