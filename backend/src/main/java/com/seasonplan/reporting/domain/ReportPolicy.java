package com.seasonplan.reporting.domain;

import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.season.api.SeasonFacts;
import com.seasonplan.monitoring.domain.ClimateMetric;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.response.domain.CorrectionAction;
import com.seasonplan.ritual.domain.RitualSession;
import com.seasonplan.pollen.domain.PollenUsage;
import com.seasonplan.shared.domain.DomainValidationException;
import java.time.ZoneOffset;
import java.util.*;

/** Report version selection, canonical source snapshots and approval/archive decisions. */
public final class ReportPolicy {
    private ReportPolicy() { }
    public static void requireGenerationState(SeasonPlanStatus status) { requireState(status, SeasonPlanStatus.ACTIVE, SeasonPlanStatus.CORRECTION, SeasonPlanStatus.EMERGENCY, SeasonPlanStatus.COMPLETED); }
    public static void requireApprovalState(SeasonPlanStatus status) { requireState(status, SeasonPlanStatus.ACTIVE, SeasonPlanStatus.COMPLETED); }
    public static void requireArchiveState(SeasonPlanStatus status) { requireState(status, SeasonPlanStatus.COMPLETED); }
    private static void requireState(SeasonPlanStatus status, SeasonPlanStatus... allowed) {
        if (Arrays.stream(allowed).noneMatch(value -> value == status)) reject("status", "operation is not allowed in " + status);
    }
    public static void requireLatestDraft(UUID selected, SeasonReport latest) {
        if (!latest.id().equals(selected)) reject("report", "only the latest draft can be approved");
    }
    public static void requireApprovableSnapshot(SeasonReport.Snapshot current, SeasonReport draft) {
        if (!current.blockers().isEmpty()) reject("completion", String.join(", ", current.blockers()));
        if (!current.equals(draft.snapshot())) reject("report", "source data changed; regenerate the report");
    }
    public static boolean requiresCompletion(SeasonPlanStatus status) { return status == SeasonPlanStatus.ACTIVE; }
    public static void requireNoCriticalForArchive(List<MagicIncident> incidents) {
        if (incidents.stream().anyMatch(MagicIncident::critical)) reject("incidents", "critical incident blocks archival");
    }
    public static void requireLatestApprovedRevision(SeasonReport approved, Optional<SeasonReport> draft) {
        if (draft.filter(value -> value.version() > approved.version()).isPresent()) reject("report", "the latest report revision must be approved before archival");
    }
    public static Optional<SeasonReport> selectCurrent(Optional<SeasonReport> draft, Optional<SeasonReport> approved) {
        return draft.filter(value -> approved.isEmpty() || value.version() > approved.get().version()).or(() -> approved);
    }
    public static SeasonReport requireApproved(Optional<SeasonReport> report) {
        if (report.isEmpty() || report.get().status() != SeasonReport.Status.APPROVED) reject("report", "an approved stored snapshot is required");
        return report.orElseThrow();
    }
    public static List<SeasonReport> historical(List<SeasonReport> reports, String catalogVersion, int currentYear) {
        return reports.stream().filter(report -> report.status() == SeasonReport.Status.APPROVED && report.snapshot().catalogVersion().equals(catalogVersion))
                .filter(report -> { int year = report.approvedAt().atZone(ZoneOffset.UTC).getYear(); return year >= currentYear - 3 && year < currentYear; })
                .sorted(Comparator.comparing(SeasonReport::approvedAt).thenComparing(SeasonReport::id)).toList();
    }
    public static SeasonReport.Snapshot snapshot(SeasonFacts plan, String version, List<SeasonReport.TaskResult> tasks,
            List<ClimateMetric> metrics, List<MagicIncident> incidents, List<CorrectionAction> actions, List<RitualSession> rituals, PollenUsage pollen) {
        return new SeasonReport.Snapshot(plan.seasonType(), version, plan.targetTerritories(), plan.naturalPhenomenonParameters(), plan.targetClimateMetrics(),
                tasks.stream().sorted(Comparator.comparing(SeasonReport.TaskResult::id)).toList(), metrics.stream().sorted(Comparator.comparing(ClimateMetric::id)).toList(),
                incidents.stream().sorted(Comparator.comparing(MagicIncident::id)).toList(), actions.stream().sorted(Comparator.comparing(CorrectionAction::id)).toList(),
                rituals.stream().sorted(Comparator.comparing(RitualSession::id)).toList(), pollen);
    }
    private static void reject(String field, String message) { throw new DomainValidationException(Map.of(field, List.of(message))); }
}
