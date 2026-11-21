package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.authorization.LifecycleAccessPolicy;
import com.seasonplan.lifecycle.authorization.LifecyclePermission;

import com.seasonplan.lifecycle.repository.SeasonPlanRepository;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.lifecycle.exception.NotFoundException;
import com.seasonplan.lifecycle.repository.RitualSessionRepository;
import com.seasonplan.lifecycle.repository.ClimateMetricRepository;
import com.seasonplan.lifecycle.repository.MagicIncidentRepository;
import com.seasonplan.lifecycle.repository.CorrectionActionRepository;
import com.seasonplan.lifecycle.repository.SeasonReportRepository;

import com.seasonplan.lifecycle.domain.*;


import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;

/** Report snapshots, approval with atomic plan completion, approved-snapshot export and archival. */
public class SeasonReportService {
    public enum Format { HTML, PDF }
    public record Export(String filename, String contentType, byte[] content) {
        public Export { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
    private final SeasonPlanRepository plans;
    private final RequiredTaskResults tasks;
    private final RitualSessionRepository rituals;
    private final ClimateMetricRepository metrics;
    private final MagicIncidentRepository incidents;
    private final CorrectionActionRepository actions;
    private final PollenService pollen;
    private final SeasonReportRepository reports;
    private final CatalogVersionProvider catalog;
    private final SeasonReportExporter exporter;
    private final CommandTransaction transactions;
    private final AuditLogService audit;
    private final Clock clock;

    public SeasonReportService(SeasonPlanRepository plans, RequiredTaskResults tasks, RitualSessionRepository rituals,
                             ClimateMetricRepository metrics, MagicIncidentRepository incidents, CorrectionActionRepository actions,
                             PollenService pollen, SeasonReportRepository reports, CatalogVersionProvider catalog,
                             SeasonReportExporter exporter, CommandTransaction transactions, AuditLogService audit, Clock clock) {
        this.plans = plans; this.tasks = tasks; this.rituals = rituals; this.metrics = metrics; this.incidents = incidents;
        this.actions = actions; this.pollen = pollen; this.reports = reports; this.catalog = catalog;
        this.exporter = exporter; this.transactions = transactions; this.audit = audit; this.clock = clock;
    }
    public SeasonReport generate(UUID planId, String title, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.GENERATE_REPORT);
        return transactions.execute(planId, () -> {
            SeasonPlan plan = ServiceSupport.plan(plans, planId, SeasonPlanStatus.ACTIVE, SeasonPlanStatus.COMPLETED);
            LifecycleAccessPolicy.requireReportOwner(role, actorId, plan);
            SeasonReport.Snapshot snapshot = snapshot(plan);
            SeasonReport report = new SeasonReport(UUID.randomUUID(), planId, reports.nextVersion(planId), title, snapshot,
                    snapshot.blockers(), SeasonReport.Status.DRAFT, actorId, clock.instant(), null, null);
            reports.saveDraft(report);
            audit.append(planId, AuditAction.SEASON_REPORT_GENERATED, actorId, report.id().toString());
            return report;
        });
    }
    public SeasonReport approveAndComplete(UUID planId, UUID reportId, ActorRole role, String actorId) {
        LifecycleAccessPolicy.require(role, LifecyclePermission.APPROVE_REPORT);
        return transactions.execute(planId, () -> {
            SeasonPlan plan = ServiceSupport.copy(ServiceSupport.plan(plans, planId, SeasonPlanStatus.ACTIVE, SeasonPlanStatus.COMPLETED));
            SeasonReport draft = reports.latestDraft(planId).orElseThrow(() -> new NotFoundException("draft report not found"));
            if (!draft.id().equals(reportId)) ServiceSupport.reject("report", "only the latest draft can be approved");
            SeasonReport.Snapshot current = snapshot(plan);
            List<String> blockers = current.blockers();
            if (!blockers.isEmpty()) ServiceSupport.reject("completion", String.join(", ", blockers));
            if (!current.equals(draft.snapshot())) ServiceSupport.reject("report", "source data changed; regenerate the report");
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
        LifecycleAccessPolicy.require(role, LifecyclePermission.ARCHIVE_PLAN);
        return transactions.execute(planId, () -> {
            SeasonPlan plan = ServiceSupport.copy(ServiceSupport.plan(plans, planId, SeasonPlanStatus.COMPLETED));
            if (incidents.forPlan(planId).stream().anyMatch(MagicIncident::critical)) ServiceSupport.reject("incidents", "critical incident blocks archival");
            SeasonReport approved = approved(planId);
            if (reports.latestDraft(planId).filter(draft -> draft.version() > approved.version()).isPresent()) {
                ServiceSupport.reject("report", "the latest report revision must be approved before archival");
            }
            plan.archive(approved, actorId, clock.instant());
            plans.save(plan);
            audit.append(planId, AuditAction.SEASON_PLAN_ARCHIVED, actorId, approved.id().toString());
            return plan;
        });
    }
    public Optional<SeasonReport> view(UUID planId) {
        ServiceSupport.plan(plans, planId);
        Optional<SeasonReport> approved = reports.latestApproved(planId);
        Optional<SeasonReport> draft = reports.latestDraft(planId);
        return draft.filter(value -> approved.isEmpty() || value.version() > approved.get().version()).or(() -> approved);
    }
    public Export export(UUID planId, Format format) {
        ServiceSupport.plan(plans, planId);
        SeasonReport report = approved(planId);
        String extension = format.name().toLowerCase(Locale.ROOT);
        return new Export("season-" + planId + "-v" + report.version() + "." + extension,
                format == Format.PDF ? "application/pdf" : "text/html; charset=UTF-8", exporter.render(report, extension));
    }
    public List<SeasonReport> historicalComparison(UUID planId) {
        SeasonPlan plan = ServiceSupport.plan(plans, planId);
        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();
        String version = catalog.forPlan(planId);
        return reports.approvedForSeasonType(plan.seasonType()).stream()
                .filter(report -> report.status() == SeasonReport.Status.APPROVED && report.snapshot().catalogVersion().equals(version))
                .filter(report -> { int approvedYear = report.approvedAt().atZone(ZoneOffset.UTC).getYear(); return approvedYear >= year - 3 && approvedYear < year; })
                .sorted(Comparator.comparing(SeasonReport::approvedAt).thenComparing(SeasonReport::id)).toList();
    }
    private SeasonReport approved(UUID planId) {
        var report = reports.latestApproved(planId);
        if (report.isEmpty() || report.get().status() != SeasonReport.Status.APPROVED) ServiceSupport.reject("report", "an approved stored snapshot is required");
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
                pollen.usage(id).orElse(null));
    }
}
