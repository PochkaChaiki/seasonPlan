package com.seasonplan.response.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Applies validated metric-domain decisions inside the caller's season command transaction. */
public interface MetricReactions {
    enum IncidentType { RITUAL, POLLEN, CLIMATE, ASSIGNMENT, SECURITY }
    enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    record IncidentSpec(IncidentType type, Severity severity, String cause, String consequences) { }
    record CorrectionSpec(String key, String description, String owner, Instant deadline) { }
    record AlertSpec(String type, Set<String> recipients, String message) {
        public AlertSpec { recipients = Set.copyOf(recipients); }
    }
    record Reaction(IncidentSpec incident, CorrectionSpec correction, AlertSpec criticalAlert, AlertSpec correctionAlert) {
        public boolean hasIncident() { return incident != null; }
        public boolean hasCorrection() { return correction != null; }
    }
    record Result(UUID incidentId, UUID correctionId) { }
    /** metricId is the stable persisted measurement identity, including on retry. */
    Result react(UUID planId, UUID metricId, Reaction specification, String actorId);
}
