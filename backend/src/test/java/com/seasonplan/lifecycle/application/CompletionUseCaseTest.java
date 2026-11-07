package com.seasonplan.lifecycle.application;

import com.seasonplan.lifecycle.application.completion.*;
import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CompletionUseCaseTest {
    private UseCaseFixture f;
    @BeforeEach void setUp() { f = new UseCaseFixture(SeasonPlanStatus.ACTIVE); f.successfulSources(); }

    @Test void guardianConfirmsImmutableReportBeforeCompletionAndArchival() {
        SeasonReport draft = f.completion.generate(f.plan.id(), "Итоги", ActorRole.FAIRY_PLANNER, "planner");
        assertThat(draft.blockers()).isEmpty();
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.ACTIVE);
        SeasonReport approved = f.completion.approveAndComplete(f.plan.id(), draft.id(), ActorRole.CHIEF_GUARDIAN, "guardian");
        assertThat(approved.status()).isEqualTo(SeasonReport.Status.APPROVED);
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.COMPLETED);
        assertThat(approved.snapshot().kpis().pollenVariance()).isEqualByComparingTo("-20.000");
        assertThat(f.completion.archive(f.plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian").status()).isEqualTo(SeasonPlanStatus.ARCHIVED);
        assertThatThrownBy(() -> f.completion.generate(f.plan.id(), "Изменение", ActorRole.FAIRY_PLANNER, "planner"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> approved.snapshot().metrics().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void incompleteDataCreatesBlockedDraftAndCriticalIncidentPreventsCompletion() {
        f.sessions.clear(); f.metrics.clear();
        SeasonReport draft = f.completion.generate(f.plan.id(), "Итоги", ActorRole.FAIRY_PLANNER, "planner");
        assertThat(draft.blockers()).contains("ritual-success", "climate-metrics");
        assertThatThrownBy(() -> f.completion.approveAndComplete(f.plan.id(), draft.id(), ActorRole.CHIEF_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        f.successfulSources();
        f.response.registerIncident(f.plan.id(), MagicIncident.Type.CLIMATE, "Причина", "Последствия", ActorRole.MAGIC_ANOMALY_CONTROLLER, "controller");
        SeasonReport blocked = f.completion.generate(f.plan.id(), "Итоги", ActorRole.FAIRY_PLANNER, "planner");
        assertThat(blocked.blockers()).contains("critical-incidents");
        assertThatThrownBy(() -> f.completion.approveAndComplete(f.plan.id(), blocked.id(), ActorRole.CHIEF_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test void staleSourcesRequireRegenerationAndOldDraftCannotBeApproved() {
        SeasonReport first = f.completion.generate(f.plan.id(), "Первая версия", ActorRole.FAIRY_PLANNER, "planner");
        f.pollenSummary = new com.seasonplan.lifecycle.application.port.PollenTree.Summary(f.pollenSummary.reservationId(), new BigDecimal("100"), new BigDecimal("90"), "v2");
        assertThatThrownBy(() -> f.completion.approveAndComplete(f.plan.id(), first.id(), ActorRole.CHIEF_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        SeasonReport second = f.completion.generate(f.plan.id(), "Вторая версия", ActorRole.FAIRY_PLANNER, "planner");
        assertThat(second.version()).isEqualTo(first.version() + 1);
        assertThatThrownBy(() -> f.completion.approveAndComplete(f.plan.id(), first.id(), ActorRole.CHIEF_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(f.completion.approveAndComplete(f.plan.id(), second.id(), ActorRole.CHIEF_GUARDIAN, "guardian").title()).isEqualTo("Вторая версия");
    }

    @Test void unfinishedTasksAndActionsBlockCompletionAndExportRequiresApprovedSnapshot() {
        f.tasks.set(0, new SeasonReport.TaskResult(f.tasks.get(0).id(), "Work", false));
        f.response.createAction(f.plan.id(), null, "Исправить", "owner", f.now.plusSeconds(3600), ActorRole.CLIMATE_BALANCER, "balancer");
        SeasonReport draft = f.completion.generate(f.plan.id(), "Итоги", ActorRole.FAIRY_PLANNER, "planner");
        assertThat(draft.blockers()).contains("completed-tasks", "completed-corrections");
        assertThatThrownBy(() -> f.completion.export(f.plan.id(), CompletionService.Format.HTML)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> f.completion.approveAndComplete(f.plan.id(), draft.id(), ActorRole.FAIRY_PLANNER, "planner"))
                .isInstanceOf(com.seasonplan.lifecycle.application.shared.AccessDeniedForRoleException.class);
    }

    @Test void revisedPresentationCreatesNewDraftWithoutChangingApprovedExport() {
        SeasonReport first = f.completion.generate(f.plan.id(), "Исходный отчёт", ActorRole.FAIRY_PLANNER, "planner");
        SeasonReport approved = f.completion.approveAndComplete(f.plan.id(), first.id(), ActorRole.CHIEF_GUARDIAN, "guardian");
        SeasonReport revision = f.completion.generate(f.plan.id(), "Новый заголовок", ActorRole.FAIRY_PLANNER, "planner");
        assertThat(revision.version()).isEqualTo(2);
        assertThat(f.completion.view(f.plan.id()).orElseThrow()).isEqualTo(revision);
        f.completion.export(f.plan.id(), CompletionService.Format.HTML);
        assertThat(f.exported).isEqualTo(approved);
        SeasonReport revisedApproval = f.completion.approveAndComplete(f.plan.id(), revision.id(), ActorRole.CHIEF_GUARDIAN, "guardian");
        assertThat(revisedApproval.version()).isEqualTo(2);
        assertThat(approved.title()).isEqualTo("Исходный отчёт");
        assertThat(f.plans.findById(f.plan.id()).orElseThrow().status()).isEqualTo(SeasonPlanStatus.COMPLETED);
    }

    @Test void pendingReportRevisionMustBeApprovedBeforeArchival() {
        SeasonReport first = f.completion.generate(f.plan.id(), "Исходный отчёт", ActorRole.FAIRY_PLANNER, "planner");
        f.completion.approveAndComplete(f.plan.id(), first.id(), ActorRole.CHIEF_GUARDIAN, "guardian");
        f.completion.generate(f.plan.id(), "Уточнение", ActorRole.FAIRY_PLANNER, "planner");
        assertThatThrownBy(() -> f.completion.archive(f.plan.id(), ActorRole.CHIEF_GUARDIAN, "guardian"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test void reportKpisAndHistoricalComparisonUseCapturedDataAndCatalogVersion() {
        SeasonReport first = f.completion.generate(f.plan.id(), "Итоги", ActorRole.FAIRY_PLANNER, "planner");
        var kpis = first.snapshot().kpis();
        assertThat(kpis.consumptionRatio()).isEqualByComparingTo("0.8000");
        assertThat(kpis.meanDeviation()).isEqualByComparingTo("0.0000");
        assertThat(kpis.ritualSuccessRate()).isEqualByComparingTo("1.0000");
        assertThat(kpis.criticalDeviationCount()).isZero();
        assertThat(kpis.incidentCounts()).containsEntry(MagicIncident.Severity.CRITICAL, 0L);
        for (int year : List.of(2022, 2023, 2025, 2026)) {
            var approvedAt = java.time.Instant.parse(year + "-01-01T00:00:00Z");
            f.approvedReports.add(new SeasonReport(UUID.randomUUID(), f.plan.id(), 1, "История", first.snapshot(), List.of(),
                    SeasonReport.Status.APPROVED, "planner", approvedAt, "guardian", approvedAt));
        }
        assertThat(f.completion.historicalComparison(f.plan.id())).extracting(report -> report.approvedAt().toString())
                .containsExactly("2023-01-01T00:00:00Z", "2025-01-01T00:00:00Z");
        var old = first.snapshot();
        var differentCatalog = new SeasonReport.Snapshot(old.seasonType(), "v2", old.territories(), old.phenomena(), old.targets(),
                old.tasks(), old.metrics(), old.incidents(), old.actions(), old.rituals(), old.pollen());
        f.approvedReports.add(new SeasonReport(UUID.randomUUID(), f.plan.id(), 1, "Другой каталог", differentCatalog, List.of(),
                SeasonReport.Status.APPROVED, "planner", f.now.minusSeconds(31536000), "guardian", f.now.minusSeconds(31536000)));
        assertThat(f.completion.historicalComparison(f.plan.id())).hasSize(2);
    }

    @Test void exportsUseOnlyApprovedVersionAndCannotMutateSnapshot() {
        SeasonReport draft = f.completion.generate(f.plan.id(), "Итоги", ActorRole.FAIRY_PLANNER, "planner");
        f.completion.approveAndComplete(f.plan.id(), draft.id(), ActorRole.CHIEF_GUARDIAN, "guardian");
        var export = f.completion.export(f.plan.id(), CompletionService.Format.PDF);
        assertThat(export.filename()).endsWith("-v1.pdf");
        assertThat(f.exported.status()).isEqualTo(SeasonReport.Status.APPROVED);
    }
}
