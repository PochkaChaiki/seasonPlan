package com.seasonplan.pollen.api;

import java.util.UUID;

/** Persisted uncertain operation. Retry with the same application key; do not create another mutation. */
public final class PollenCommandPendingException extends PollenTreeUnavailableException {
    private final UUID operationId;
    public PollenCommandPendingException(UUID operationId) {
        super("Pollen operation outcome is unresolved"); this.operationId = operationId;
    }
    public UUID operationId() { return operationId; }
}
