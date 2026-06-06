package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.ActorRole;

public class AccessDeniedForRoleException extends RuntimeException {
    public AccessDeniedForRoleException(ActorRole role) {
        super("role is not allowed for this command: " + role);
    }
}
