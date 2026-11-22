package com.seasonplan.identity.domain;

import com.seasonplan.identity.api.*;

/** Account authorization and credential evidence rules. Cryptographic verification is supplied as a fact. */
public final class AccountAdministrationPolicy {
    private AccountAdministrationPolicy() { }
    public static void requireAdministrator(CurrentActor actor) {
        if (actor == null || !actor.systemAdministrator()) throw new AccessDeniedForRoleException("system administrator is required");
    }
    public static boolean canVerify(String password, String hash) { return password != null && !password.isBlank() && hash != null; }
    public static void requireCurrentPassword(String password, String hash, boolean matches) {
        if (!canVerify(password, hash) || !matches)
            throw new UserAdministrationException(UserAdministrationException.Kind.INVALID_CURRENT_PASSWORD, "Current password is incorrect");
    }
    public static boolean bootstrapRequired(boolean administratorExists) { return !administratorExists; }
}
