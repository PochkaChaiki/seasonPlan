package com.seasonplan.lifecycle.domain;

/** A historical record cannot be represented safely until an operator supplies real metadata. */
public final class LegacyExecutionMetadataException extends IllegalStateException {
    public LegacyExecutionMetadataException(String source) {
        super("Legacy " + source + " records need operator remediation before execution operations");
    }
}
