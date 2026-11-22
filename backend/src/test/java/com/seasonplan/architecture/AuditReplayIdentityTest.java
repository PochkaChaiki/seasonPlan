package com.seasonplan.architecture;

import com.seasonplan.audit.api.AuditAction;
import com.seasonplan.audit.api.AuditLogEntry;
import com.seasonplan.audit.domain.AuditReplayIdentity;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AuditReplayIdentityTest {
    @Test void replayPreservesTimestampAndRejectsDifferentContent() {
        var id = UUID.randomUUID();
        var plan = UUID.randomUUID();
        var original = new AuditLogEntry(id, plan, AuditAction.SEASON_PLAN_CREATED, "actor", Instant.EPOCH, null);
        var replay = new AuditLogEntry(id, plan, AuditAction.SEASON_PLAN_CREATED, "actor", Instant.now(), null);
        assertThat(AuditReplayIdentity.preserveOriginal(original, replay)).isSameAs(original);
        var changed = new AuditLogEntry(id, plan, AuditAction.SEASON_PLAN_CREATED, "other", Instant.now(), null);
        assertThatThrownBy(() -> AuditReplayIdentity.preserveOriginal(original, changed)).isInstanceOf(IllegalStateException.class);
    }
}
