package com.seasonplan.lifecycle.application.completion;

import com.seasonplan.lifecycle.application.UseCaseSupport;
import com.seasonplan.lifecycle.application.port.*;
import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.application.shared.*;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;

/** UC-5: draft/regenerate, confirm results, complete, approved-snapshot export and archive. */
public class CompletionService {
    public enum Format { HTML, PDF }
    public record Export(String filename, String contentType, byte[] content) {
        public Export { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
    private final SeasonPlanRepository plans;
    private final CompletionPorts.Tasks tasks;
    private final ExecutionPorts.Rituals rituals;
    private final ExecutionPorts.Metrics metrics;
    private final ExecutionPorts.Incidents incidents;
    private final ExecutionPorts.Corrections actions;
    private final PollenTree pollen;
    private final CompletionPorts.Reports reports;
    private final CompletionPorts.CatalogVersion catalog;
    private final CompletionPorts.Exporter exporter;
    private final CommandTransaction transactions;
    private final AuditLogService audit;
    private final Clock clock;

    public CompletionService(SeasonPlanRepository plans, CompletionPorts.Tasks tasks, ExecutionPorts.Rituals rituals,
                             ExecutionPorts.Metrics metrics, ExecutionPorts.Incidents incidents, ExecutionPorts.Corrections actions,
                             PollenTree pollen, CompletionPorts.Reports reports, CompletionPorts.CatalogVersion catalog,
                             CompletionPorts.Exporter exporter, CommandTransaction transactions, AuditLogService audit, Clock clock) {
        this.plans = plans; this.tasks = tasks; this.rituals = rituals; this.metrics = metrics; this.incidents = incidents;
        this.actions = actions; this.pollen = pollen; this.reports = reports; this.catalog = catalog;
        this.exporter = exporter; this.transactions = transactions; this.audit = audit; this.clock = clock;
    }
    public SeasonReport generate(UUID planId, String title, ActorRole role, String actorId) {
        ValidationSupport.requireAnyRole(role, ActorRole.FAIRY_PLANNER, ActorRole.CHIEF_GUARDIAN);
        return transactions.execute(planId, () -> {
            SeasonPlan plan = UseCaseSupport.plan(plans, planId, SeasonPlanStatus.ACTIVE, SeasonPlanStatus.COMPLETED);
            if (role == ActorRole.FAIRY_PLANNER && !actorId.equals(plan.createdBy())) throw new AccessDeniedForRoleException(role);
            SeasonReport.Snapshot snapshot = snapshot(plan);
            SeasonReport report = new SeasonReport(UUID.randomUUID(), planId, reports.nextVersion(planId), title, snapshot,
                    snapshot.blockers(), SeasonReport.Status.DRAFT, actorId, clock.instant(), null, null);
            reports.saveDraft(report);
            audit.append(planId, AuditAction.SEASON_REPORT_GENERATED, actorId, report.id().toString());
            return report;
        });
    }
    public SeasonReport approveAndComplete(UUID planId, UUID reportId, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.CHIEF_GUARDIAN);
        return transactions.execute(planId, () -> {
            SeasonPlan plan = UseCaseSupport.copy(UseCaseSupport.plan(plans, planId, SeasonPlanStatus.ACTIVE, SeasonPlanStatus.COMPLETED));
            SeasonReport draft = reports.latestDraft(planId).orElseThrow(() -> new NotFoundException("draft report not found"));
            if (!draft.id().equals(reportId)) UseCaseSupport.reject("report", "only the latest draft can be approved");
            SeasonReport.Snapshot current = snapshot(plan);
            List<String> blockers = current.blockers();
            if (!blockers.isEmpty()) UseCaseSupport.reject("completion", String.join(", ", blockers));
            if (!current.equals(draft.snapshot())) UseCaseSupport.reject("report", "source data changed; regenerate the report");
            SeasonReport approved = draft.approve(actorId, clock.instant());
            boolean completing = plan.status() == SeasonPlanStatus.ACTIVE;
            if (completing) plan.complete(approved, actorId, clock.instant());
            reports.appendApproved(approved);
            audit.append(planId, AuditAction.SEASON_REPORT_APPROVED, actorId, approved.id().toString());
            if (completing) {
                plans.save(plan);
                audit.append(planId, AuditAction.SEASON_PLAN_COMPLETED, actorId, approved.id().toString());
            }
            return approved;
        });
    }
    public SeasonPlan archive(UUID planId, ActorRole role, String actorId) {
        ValidationSupport.requireRole(role, ActorRole.CHIEF_GUARDIAN);
        return transactions.execute(planId, () -> {
            SeasonPlan plan = UseCaseSupport.copy(UseCaseSupport.plan(plans, planId, SeasonPlanStatus.COMPLETED));
            if (incidents.forPlan(planId).stream().anyMatch(MagicIncident::critical)) UseCaseSupport.reject("incidents", "critical incident blocks archival");
            SeasonReport approved = approved(planId);
            if (reports.latestDraft(planId).filter(draft -> draft.version() > approved.version()).isPresent()) {
                UseCaseSupport.reject("report", "the latest report revision must be approved before archival");
            }
            plan.archive(approved, actorId, clock.instant());
            plans.save(plan);
            audit.append(planId, AuditAction.SEASON_PLAN_ARCHIVED, actorId, approved.id().toString());
            return plan;
        });
    }
    public Optional<SeasonReport> view(UUID planId) {
        UseCaseSupport.plan(plans, planId);
        Optional<SeasonReport> approved = reports.latestApproved(planId);
        Optional<SeasonReport> draft = reports.latestDraft(planId);
        return draft.filter(value -> approved.isEmpty() || value.version() > approved.get().version()).or(() -> approved);
    }
    public Export export(UUID planId, Format format) {
        UseCaseSupport.plan(plans, planId);
        SeasonReport report = approved(planId);
        String extension = format.name().toLowerCase(Locale.ROOT);
        return new Export("season-" + planId + "-v" + report.version() + "." + extension,
                format == Format.PDF ? "application/pdf" : "text/html; charset=UTF-8", exporter.render(report, extension));
    }
    public List<SeasonReport> historicalComparison(UUID planId) {
        SeasonPlan plan = UseCaseSupport.plan(plans, planId);
        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();
        String version = catalog.forPlan(planId);
        return reports.approvedForSeasonType(plan.seasonType()).stream()
                .filter(report -> report.status() == SeasonReport.Status.APPROVED && report.snapshot().catalogVersion().equals(version))
                .filter(report -> { int approvedYear = report.approvedAt().atZone(ZoneOffset.UTC).getYear(); return approvedYear >= year - 3 && approvedYear < year; })
                .sorted(Comparator.comparing(SeasonReport::approvedAt).thenComparing(SeasonReport::id)).toList();
    }
    private SeasonReport approved(UUID planId) {
        var report = reports.latestApproved(planId);
        if (report.isEmpty() || report.get().status() != SeasonReport.Status.APPROVED) UseCaseSupport.reject("report", "an approved stored snapshot is required");
        return report.orElseThrow();
    }
    private SeasonReport.Snapshot snapshot(SeasonPlan plan) {
        UUID id = plan.id();
        return new SeasonReport.Snapshot(plan.seasonType(), catalog.forPlan(id), plan.targetTerritories(), plan.naturalPhenomenonParameters(), plan.targetClimateMetrics(),
                tasks.requiredResults(id).stream().sorted(Comparator.comparing(SeasonReport.TaskResult::id)).toList(),
                metrics.forPlan(id).stream().sorted(Comparator.comparing(ClimateMetric::id)).toList(),
                incidents.forPlan(id).stream().sorted(Comparator.comparing(MagicIncident::id)).toList(),
                actions.forPlan(id).stream().sorted(Comparator.comparing(CorrectionAction::id)).toList(),
                rituals.forPlan(id).stream().sorted(Comparator.comparing(RitualSession::id)).toList(),
                pollen.summary(id).map(PollenTree.Summary::snapshot).orElse(null));
    }
}
