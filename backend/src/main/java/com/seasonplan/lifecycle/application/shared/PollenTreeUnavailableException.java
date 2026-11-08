package com.seasonplan.lifecycle.application.shared;

/** Tree could not supply an authoritative result; callers must not fall back to local projections. */
public class PollenTreeUnavailableException extends RuntimeException {
    public PollenTreeUnavailableException(String message) {
        super(message);
    }

    public PollenTreeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
