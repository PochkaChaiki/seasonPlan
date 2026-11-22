package com.seasonplan.pollen.domain;

import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.DomainValidationException;
import java.util.List;
import java.util.Map;

/** Funding identity and terminal-season controls, evaluated from owner-supplied facts only. */
public final class PollenIntentPolicy {
    private PollenIntentPolicy() { }
    public static boolean terminal(SeasonPlanStatus status) {
        return status == SeasonPlanStatus.COMPLETED || status == SeasonPlanStatus.ARCHIVED || status == SeasonPlanStatus.CANCELLED;
    }
    public static void requireMutable(SeasonPlanStatus status) {
        if (terminal(status)) reject("status", "terminal season resource operations are frozen");
    }
    public static void requireSettled(java.util.Optional<java.util.UUID> unresolved) {
        unresolved.ifPresent(id -> { throw new com.seasonplan.pollen.api.PollenCommandPendingException(id); });
    }
    public static void requireBinding(ReservationBinding binding, long revision) { requireRevision(binding.materialRevision(), revision); }
    public static void requireRevision(long captured, long current) { if (captured != current) obsolete(); }
    public static void obsolete() { reject("materialRevision", "resource evidence belongs to an obsolete material revision; request current resources"); }
    private static void reject(String field, String text) { throw new DomainValidationException(Map.of(field, List.of(text))); }
}
