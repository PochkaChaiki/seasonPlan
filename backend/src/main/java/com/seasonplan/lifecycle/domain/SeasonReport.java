package com.seasonplan.lifecycle.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Versioned immutable report content; approval seals exactly the captured sources. */
public record SeasonReport(UUID id, UUID seasonPlanId, int version, String title, Snapshot snapshot,
                           List<String> blockers, Status status, String createdBy, Instant createdAt,
                           String approvedBy, Instant approvedAt) {
    public enum Status { DRAFT, APPROVED }
    /** Completion projection of required work; independent of the legacy preparation task adapter. */
    public record TaskResult(UUID id, String title, boolean completed) {
        public TaskResult { Objects.requireNonNull(id); Objects.requireNonNull(title); }
    }
    public record Kpis(BigDecimal pollenVariance, BigDecimal consumptionRatio,
                       Map<MagicIncident.Severity, Long> incidentCounts, BigDecimal meanDeviation,
                       long criticalDeviationCount, BigDecimal ritualSuccessRate) {
        public Kpis { incidentCounts = Map.copyOf(incidentCounts); }
    }
    public record Snapshot(String seasonType, String catalogVersion, List<String> territories,
                           Map<String, String> phenomena, Map<String, Double> targets, List<TaskResult> tasks,
                           List<ClimateMetric> metrics, List<MagicIncident> incidents,
                           List<CorrectionAction> actions, List<RitualSession> rituals, PollenUsage pollen) {
        public Snapshot {
            Objects.requireNonNull(seasonType);
            if (catalogVersion == null || catalogVersion.isBlank()) throw new IllegalArgumentException("catalog version required");
            territories = List.copyOf(territories); phenomena = Map.copyOf(phenomena); targets = Map.copyOf(targets);
            tasks = List.copyOf(tasks); metrics = List.copyOf(metrics); incidents = List.copyOf(incidents);
            actions = List.copyOf(actions); rituals = List.copyOf(rituals);
        }
        public List<String> blockers() {
            java.util.ArrayList<String> failures = new java.util.ArrayList<>();
            if (rituals.stream().noneMatch(r -> r.result() == RitualSession.Result.SUCCESS)
                    || rituals.stream().anyMatch(RitualSession::inProgress)) failures.add("ritual-success");
            if (tasks.isEmpty() || tasks.stream().anyMatch(t -> !t.completed())) failures.add("completed-tasks");
            if (actions.stream().anyMatch(a -> !a.terminal())) failures.add("completed-corrections");
            if (incidents.stream().anyMatch(MagicIncident::critical)) failures.add("critical-incidents");
            if (targets.isEmpty() || !metrics.stream().map(m -> m.definition().key()).toList().containsAll(targets.keySet())) failures.add("climate-metrics");
            if (pollen == null) failures.add("pollen-totals");
            return List.copyOf(failures);
        }
        public Kpis kpis() {
            Map<MagicIncident.Severity, Long> counts = new EnumMap<>(MagicIncident.Severity.class);
            for (var severity : MagicIncident.Severity.values()) counts.put(severity, incidents.stream().filter(i -> i.severity() == severity).count());
            BigDecimal mean = metrics.isEmpty() ? null : metrics.stream().map(ClimateMetric::deviationScore).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(metrics.size()), 4, RoundingMode.HALF_UP);
            long completed = rituals.stream().filter(r -> !r.inProgress()).count();
            long successful = rituals.stream().filter(r -> r.result() == RitualSession.Result.SUCCESS).count();
            return new Kpis(pollen == null ? null : pollen.variance(), pollen == null ? null : pollen.consumptionRatio(), counts, mean,
                    metrics.stream().filter(ClimateMetric::critical).count(), completed == 0 ? null : BigDecimal.valueOf(successful)
                    .divide(BigDecimal.valueOf(completed), 4, RoundingMode.HALF_UP));
        }
    }
    public SeasonReport {
        Objects.requireNonNull(id); Objects.requireNonNull(seasonPlanId); Objects.requireNonNull(snapshot);
        Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
        if (version < 1 || title == null || title.isBlank() || createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("report metadata is required");
        }
        blockers = List.copyOf(blockers);
        if (status == Status.APPROVED && (!blockers.isEmpty() || !snapshot.blockers().isEmpty()
                || approvedBy == null || approvedBy.isBlank() || approvedAt == null)) {
            throw new IllegalArgumentException("approval requires complete data and an approver");
        }
        if (status == Status.DRAFT && (approvedAt != null || approvedBy != null)) throw new IllegalArgumentException("draft cannot be approved");
    }
    public SeasonReport approve(String actorId, Instant now) {
        if (status != Status.DRAFT) throw new IllegalArgumentException("approved reports are immutable");
        return new SeasonReport(id, seasonPlanId, version, title, snapshot, blockers, Status.APPROVED, createdBy, createdAt, actorId, now);
    }
}
