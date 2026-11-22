package com.seasonplan.response.domain;

import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.domain.DomainValidationException;
import java.time.*;
import java.util.*;

/** Incident/correction invariants, evaluated from already loaded facts. */
public final class IncidentResponsePolicy {
    private IncidentResponsePolicy() { }
    public static boolean hasCritical(List<MagicIncident> incidents) { return incidents.stream().anyMatch(MagicIncident::critical); }
    public static void requireMutableSeason(SeasonPlanStatus status) {
        if (!EnumSet.of(SeasonPlanStatus.RITUAL_READY, SeasonPlanStatus.ACTIVE, SeasonPlanStatus.CORRECTION, SeasonPlanStatus.EMERGENCY).contains(status))
            reject("status", "operation is not allowed in " + status);
    }
    public static void requireOverrideReason(String reason) {
        if (reason == null || reason.isBlank()) reject("reason", "severity override requires a reason");
    }
    public static void requireIncidentPlan(UUID planId, MagicIncident incident) {
        if (!incident.seasonPlanId().equals(planId)) reject("incident", "incident belongs to another plan");
    }
    public static void requireResolvable(UUID id, List<CorrectionAction> actions) {
        if (actions.stream().anyMatch(action -> id.equals(action.incidentId()) && !action.terminal())) reject("correctionActions", "linked correction actions are incomplete");
    }
    public static Set<String> correctionRecipients(String owner) { return Set.of(owner); }
    public static Set<String> incidentRecipients() { return Set.of("CHIEF_GUARDIAN", "MAGIC_ANOMALY_CONTROLLER"); }
    public static Set<String> overdueRecipients(String owner) { return Set.copyOf(List.of("CHIEF_GUARDIAN", "MAGIC_ANOMALY_CONTROLLER", owner)); }
    public static long escalationLevel(CorrectionAction action, Instant now) { return Duration.between(action.deadline(), now).toDays() + 1; }
    private static void reject(String field, String message) { throw new DomainValidationException(Map.of(field, List.of(message))); }
}
