package com.seasonplan.audit.domain;

import com.seasonplan.audit.api.AuditLogEntry;
import java.util.Objects;

public final class AuditReplayIdentity {
    private AuditReplayIdentity() { }

    public static AuditLogEntry preserveOriginal(AuditLogEntry original, AuditLogEntry replay) {
        if (!Objects.equals(original.id(), replay.id())
                || !Objects.equals(original.seasonPlanId(), replay.seasonPlanId())
                || original.action() != replay.action()
                || !Objects.equals(original.actorId(), replay.actorId())
                || !Objects.equals(original.comment(), replay.comment())) {
            throw new IllegalStateException("audit event identity cannot be reused with different content");
        }
        return original;
    }
}
