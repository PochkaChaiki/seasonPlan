package com.seasonplan.season.domain;
import java.time.Instant;
import java.util.Objects;
/** Original capture provenance, independent of later metadata/status writers. */
public record MaterialRevisionStamp(long number, String actorId, Instant capturedAt, Reason reason) {
    public enum Reason { CREATE, EDIT, LEGACY_BASELINE }
    public MaterialRevisionStamp {
        if (number < 0 || actorId == null || actorId.isBlank()) throw new IllegalArgumentException("material revision identity is required");
        Objects.requireNonNull(capturedAt); Objects.requireNonNull(reason);
    }
}
