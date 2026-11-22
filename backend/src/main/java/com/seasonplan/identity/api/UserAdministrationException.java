package com.seasonplan.identity.api;

public class UserAdministrationException extends RuntimeException {
    public enum Kind { VALIDATION, DUPLICATE_USERNAME, INVALID_CURRENT_PASSWORD }

    private final Kind kind;

    public UserAdministrationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
