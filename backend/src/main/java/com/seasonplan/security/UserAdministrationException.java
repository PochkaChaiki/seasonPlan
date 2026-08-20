package com.seasonplan.security;

class UserAdministrationException extends RuntimeException {
    enum Kind { VALIDATION, DUPLICATE_USERNAME, INVALID_CURRENT_PASSWORD }

    private final Kind kind;

    UserAdministrationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    Kind kind() { return kind; }
}
