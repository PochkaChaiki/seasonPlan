package com.seasonplan.lifecycle.domain;

import com.seasonplan.ritual.domain.RitualPolicy;
import com.seasonplan.ritual.domain.RitualSession;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RitualPolicyTest {
    @Test void completionRequiresTheCurrentParticipantsAndRequiredEvents() {
        var participant = new RitualSession.Participant("fairy", "role", Instant.EPOCH);
        var session = RitualSession.start(UUID.randomUUID(), UUID.randomUUID(), 1, "actor", Instant.EPOCH, List.of(participant));
        assertEquals("READINESS_BLOCKED", RitualPolicy.completionFailure(session, List.of(), List.of(), List.of(), false).code());
        assertEquals("MISSING_EVENTS", RitualPolicy.completionFailure(session, List.of(), List.of(participant), List.of("event"), false).code());
        assertNull(RitualPolicy.completionFailure(session, List.of(), List.of(participant), List.of(), false));
    }
}
