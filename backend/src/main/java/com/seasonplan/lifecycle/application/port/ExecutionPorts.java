package com.seasonplan.lifecycle.application.port;

import com.seasonplan.lifecycle.domain.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Outbound contracts for UC-4; implementations belong outside the application layer. */
public final class ExecutionPorts {
    private ExecutionPorts() { }
    public interface Rituals {
        Optional<RitualSession> find(UUID id);
        List<RitualSession> forPlan(UUID planId);
        void save(RitualSession session);
    }
    public interface Metrics {
        Optional<ClimateMetric> byImportKey(String source, String eventId);
        List<ClimateMetric> forPlan(UUID planId);
        /** (source, sourceEventId) is globally unique. */
        void save(ClimateMetric metric);
    }
    public interface Incidents {
        Optional<MagicIncident> find(UUID id);
        List<MagicIncident> forPlan(UUID planId);
        void save(MagicIncident incident);
    }
    public interface Corrections {
        Optional<CorrectionAction> find(UUID id);
        Optional<CorrectionAction> byKey(String key);
        List<CorrectionAction> forPlan(UUID planId);
        List<CorrectionAction> allOpen();
        /** deduplicationKey is unique. */
        void save(CorrectionAction action);
    }
    public record Readiness(List<String> blockers, List<RitualSession.Participant> participants, Set<String> requiredEvents) {
        public Readiness {
            blockers = List.copyOf(blockers); participants = List.copyOf(participants); requiredEvents = Set.copyOf(requiredEvents);
            if (requiredEvents.isEmpty()) throw new IllegalArgumentException("ritual completion events must be configured");
        }
    }
    public interface LaunchReadiness { Readiness inspect(UUID planId); }
    public record MetricRule(BigDecimal target, BigDecimal tolerance, String version, String owner, Duration responseTime) {
        public MetricRule {
            ClimateMetric.score(target, target, tolerance);
            if (version == null || version.isBlank() || owner == null || owner.isBlank()
                    || responseTime == null || responseTime.isNegative() || responseTime.isZero()) {
                throw new IllegalArgumentException("a versioned metric response rule is required");
            }
        }
    }
    public interface MetricRules { MetricRule forMetric(UUID planId, MetricDefinition metric); }
    public interface IncidentRules { MagicIncident.Severity severityFor(MagicIncident.Type type); }
}
