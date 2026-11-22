package com.seasonplan.domain;

import com.seasonplan.reference.api.MetricDefinition;

import com.seasonplan.season.domain.*;
import com.seasonplan.work.domain.*;
import com.seasonplan.pollen.domain.*;
import com.seasonplan.ritual.domain.RitualReadinessPolicy;
import com.seasonplan.identity.domain.AccountAdministrationPolicy;
import com.seasonplan.identity.api.UserAdministrationException;
import com.seasonplan.monitoring.domain.ClimateMetric;
import com.seasonplan.reference.domain.ExecutionRuleSnapshot;
import com.seasonplan.shared.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Domain decisions execute without Spring, repositories, HTTP, clocks or mocks. */
class DomainPoliciesTest {
    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final TimeWindow WINDOW = new TimeWindow(LocalDateTime.parse("2026-03-01T09:00:00"), LocalDateTime.parse("2026-03-01T10:00:00"));

    @Test void missingSeasonParametersReturnDomainFieldErrors() {
        assertThatThrownBy(() -> SeasonPlanPolicy.validate(new SeasonPlan(), List.of("calendar is unavailable")))
                .isInstanceOfSatisfying(DomainValidationException.class, ex -> assertThat(ex.fieldErrors()).containsKeys("seasonType", "timeWindows", "targetTerritories", "targetClimateMetrics", "naturalCalendar"));
    }
    @Test void seasonWindowConflictUsesNormalizedTerritoriesAndHalfOpenIntervals() {
        SeasonPlan first = plan(" north "); first.submit("planner", NOW);
        SeasonPlan other = plan("NORTH");
        assertThatThrownBy(() -> SeasonPlanPolicy.requireNoWindowConflict(other, List.of(first))).isInstanceOf(DomainValidationException.class);
        other.setTimeWindows(List.of(new TimeWindow(WINDOW.end(), WINDOW.end().plusHours(1))));
        assertThatCode(() -> SeasonPlanPolicy.requireNoWindowConflict(other, List.of(first))).doesNotThrowAnyException();
    }
    @Test void assignedTaskCannotCompleteWithoutTimestampedCompatibleEvidence() {
        MagicTask task = task(MagicTaskStatus.READY), completed = task(MagicTaskStatus.COMPLETED);
        var legacy = new FairyAssignment(UUID.randomUUID(), task.id(), "fairy", "LEAD", "WATER", AssignmentStatus.CONFIRMED);
        assertThatThrownBy(() -> TaskPreparationPolicy.validateUpdate(task, completed, rules(), List.of(legacy))).isInstanceOf(DomainValidationException.class);
        var confirmed = new FairyAssignment(UUID.randomUUID(), task.id(), "fairy", "LEAD", "WATER", AssignmentStatus.CONFIRMED, NOW);
        assertThatCode(() -> TaskPreparationPolicy.validateUpdate(task, completed, rules(), List.of(confirmed))).doesNotThrowAnyException();
        assertThat(TaskPreparationPolicy.scheduleConflict(UUID.randomUUID(), WINDOW, List.of(task))).isTrue();
        assertThat(TaskPreparationPolicy.scheduleConflict(task.id(), WINDOW, List.of(task))).isFalse();
    }
    @Test void resourceWarningsUseExistingEightyHundredAndOverrunBoundaries() {
        UUID id = UUID.randomUUID();
        assertThat(PollenConsumptionPolicy.warnings(new PollenUsage(id, new BigDecimal("100"), new BigDecimal("79.999"), "v1"))).isEmpty();
        assertThat(PollenConsumptionPolicy.warnings(new PollenUsage(id, new BigDecimal("100"), new BigDecimal("100"), "v1")))
                .extracting(PollenConsumptionPolicy.Warning::threshold).containsExactly("80", "100");
        assertThat(PollenConsumptionPolicy.warnings(new PollenUsage(id, new BigDecimal("100"), new BigDecimal("100.001"), "v1")))
                .extracting(PollenConsumptionPolicy.Warning::threshold).containsExactly("80", "100", "OVER_100");
        assertThatThrownBy(() -> PollenConsumptionPolicy.normalize(new BigDecimal("0.0001"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void ritualReadinessDependsOnDomainEvidenceNotManualFlagsAlone() {
        SeasonPlan plan = plan("north"); plan.pinExecutionRules(rules());
        MagicTask task = task(MagicTaskStatus.COMPLETED); task.setSeasonPlanId(plan.id());
        var reservation = new PollenReservation(UUID.randomUUID(), plan.id(), 100); reservation.confirm(100, "ok", "guardian");
        var assignment = new FairyAssignment(UUID.randomUUID(), task.id(), "fairy", "LEAD", "WATER", AssignmentStatus.CONFIRMED, NOW);
        var attached = new java.util.ArrayList<>(List.of(assignmentFacts(assignment)));
        var taskEvidence = new RitualReadinessPolicy.TaskFacts(taskFacts(task), attached);
        attached.clear();
        assertThat(taskEvidence.assignments()).hasSize(1);
        assertThatThrownBy(() -> taskEvidence.assignments().clear()).isInstanceOf(UnsupportedOperationException.class);
        var ready = new RitualReadinessPolicy().evaluate(seasonFacts(plan), Optional.of(reservationFacts(reservation)), Optional.of(reservationFacts(reservation)),
                List.of(taskEvidence), List.of());
        assertThat(ready.blockers()).isEmpty();
        var blocked = new RitualReadinessPolicy().evaluate(seasonFacts(plan), Optional.of(reservationFacts(reservation)), Optional.of(reservationFacts(reservation)),
                List.of(new RitualReadinessPolicy.TaskFacts(taskFacts(task), List.of())), List.of());
        assertThat(blocked.blockers()).contains("assignment:" + task.id(), "role:LEAD");
    }
    @Test void passwordEvidenceAndMetricReplayAreDomainDecisions() {
        assertThat(AccountAdministrationPolicy.canVerify("", "hash")).isFalse();
        assertThatThrownBy(() -> AccountAdministrationPolicy.requireCurrentPassword("password", "hash", false)).isInstanceOf(UserAdministrationException.class);
        var definition = ClimateMetric.definitionFor("temperature", "CELSIUS");
        UUID id = UUID.randomUUID(), plan = UUID.randomUUID();
        var metric = new ClimateMetric(id, plan, "sensor", "event", definition, new BigDecimal("20.00"), new BigDecimal("20"), new BigDecimal("2"), null, NOW);
        assertThatCode(() -> metric.requireSameObservation(plan, definition, new BigDecimal("20"), NOW)).doesNotThrowAnyException();
        assertThatThrownBy(() -> metric.requireSameObservation(plan, definition, new BigDecimal("21"), NOW)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void seasonalRequirementsUseTheExplicitExistingDomainCatalog() {
        var catalog = com.seasonplan.reference.domain.SeasonalRequirementsCatalog.requirements();
        assertThat(com.seasonplan.reference.domain.SeasonalRequirementsCatalog.POLICY_VERSION).isNotBlank();
        assertThat(catalog.keySet()).containsExactlyInAnyOrder("SPRING", "SUMMER", "AUTUMN", "WINTER");
        assertThat(catalog.get("SPRING").categories()).containsExactlyInAnyOrder("WEATHER", "POLLEN");
        assertThat(catalog.get("WINTER").roles()).containsExactlyInAnyOrder("RITUAL_LEAD", "FROST_WARDEN");
        assertThatThrownBy(() -> catalog.clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void metricReactionDecidesIncidentCorrectionDeadlineAndRecipientsWithoutIo() {
        UUID plan = UUID.randomUUID();
        var definition = ClimateMetric.definitionFor("temperature", "CELSIUS");
        var normal = new ClimateMetric(UUID.randomUUID(), plan, "sensor", "normal", definition, new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("2"), null, NOW);
        var warning = new ClimateMetric(UUID.randomUUID(), plan, "sensor", "warning", definition, new BigDecimal("22"), new BigDecimal("20"), new BigDecimal("2"), null, NOW);
        var critical = new ClimateMetric(UUID.randomUUID(), plan, "sensor", "critical", definition, new BigDecimal("24"), new BigDecimal("20"), new BigDecimal("2"), null, NOW);
        var none = com.seasonplan.monitoring.domain.MetricReactionPolicy.evaluate(normal, "v1", "owner", Duration.ofMinutes(1), NOW);
        assertThat(none.hasIncident()).isFalse(); assertThat(none.hasCorrection()).isFalse();
        var correction = com.seasonplan.monitoring.domain.MetricReactionPolicy.evaluate(warning, "v1", "owner", Duration.ofMinutes(1), NOW);
        assertThat(correction.hasIncident()).isFalse(); assertThat(correction.correction().deadline()).isEqualTo(NOW.plusSeconds(60));
        assertThat(correction.correctionAlert().recipients()).containsExactly("owner");
        var emergency = com.seasonplan.monitoring.domain.MetricReactionPolicy.evaluate(critical, "v1", "CHIEF_GUARDIAN", Duration.ofMinutes(1), NOW);
        assertThat(emergency.incident().severity()).isEqualTo(com.seasonplan.response.api.MetricReactions.Severity.CRITICAL);
        assertThat(emergency.criticalAlert().recipients()).containsExactlyInAnyOrder("CHIEF_GUARDIAN", "MAGIC_ANOMALY_CONTROLLER");
        assertThat(emergency.correctionAlert()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = SeasonPlanStatus.class, names = {"COMPLETED", "ARCHIVED", "CANCELLED"})
    void terminalSeasonChecklistCannotBeChanged(SeasonPlanStatus status) {
        SeasonPlan source = plan("north");
        var terminal = SeasonPlan.rehydrate(source.id(), source.seasonType(), source.targetTerritories(), source.timeWindows(),
                source.naturalPhenomenonParameters(), source.targetClimateMetrics(), status, null, source.readinessChecklist(),
                source.createdBy(), source.updatedBy(), source.createdAt(), source.updatedAt(), source.materialRevisionStamp());
        assertThatThrownBy(() -> terminal.completeReadinessChecklistItem("late", "coordinator", NOW)).isInstanceOf(DomainValidationException.class);
        assertThat(terminal.readinessChecklist()).extracting(ChecklistItem::key).doesNotContain("late");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(SeasonPlanStatus.class)
    void operationSpecificEligibilityMatchesTheExistingDomainRules(SeasonPlanStatus status) {
        assertEligibility(() -> PollenConsumptionPolicy.requireConsumptionState(status), status == SeasonPlanStatus.ACTIVE);
        assertEligibility(() -> com.seasonplan.monitoring.domain.MetricReactionPolicy.requireRecordingState(status),
                Set.of(SeasonPlanStatus.ACTIVE, SeasonPlanStatus.CORRECTION, SeasonPlanStatus.EMERGENCY).contains(status));
        assertEligibility(() -> com.seasonplan.reporting.domain.ReportPolicy.requireGenerationState(status),
                Set.of(SeasonPlanStatus.ACTIVE, SeasonPlanStatus.CORRECTION, SeasonPlanStatus.EMERGENCY, SeasonPlanStatus.COMPLETED).contains(status));
        assertEligibility(() -> com.seasonplan.reporting.domain.ReportPolicy.requireApprovalState(status),
                Set.of(SeasonPlanStatus.ACTIVE, SeasonPlanStatus.COMPLETED).contains(status));
        assertEligibility(() -> com.seasonplan.reporting.domain.ReportPolicy.requireArchiveState(status), status == SeasonPlanStatus.COMPLETED);
        assertEligibility(() -> com.seasonplan.ritual.domain.RitualPolicy.requireStartStatus(status), status == SeasonPlanStatus.RITUAL_READY);
    }
    @Test void domainOwnsInitialReadinessItemsAndReviewCorrectionRecipients() {
        var items = SeasonPlanPolicy.initialReadinessChecklist();
        assertThat(items).extracting(ChecklistItem::key).containsExactly("calendar-reviewed", "roles-reviewed");
        assertThat(items).allMatch(item -> !item.complete());
        assertThat(SeasonPlanPolicy.reviewRecipients(plan("north"))).containsExactly("planner");
        assertThat(com.seasonplan.response.domain.IncidentResponsePolicy.correctionRecipients("owner")).containsExactly("owner");
    }
    private static void assertEligibility(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, boolean allowed) {
        if (allowed) assertThatCode(operation).doesNotThrowAnyException();
        else assertThatThrownBy(operation).isInstanceOf(DomainValidationException.class);
    }

    @Test void metricRuleSelectionUsesImmutableSeasonFactsAndRejectsMissingPinnedData() {
        var plan = plan("north");
        assertThatThrownBy(() -> com.seasonplan.monitoring.domain.MetricRulePolicy.select(seasonFacts(plan), MetricDefinition.TEMPERATURE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("pinned execution rules");
        plan.pinExecutionRules(rules());
        var selected = com.seasonplan.monitoring.domain.MetricRulePolicy.select(seasonFacts(plan), MetricDefinition.TEMPERATURE);
        assertThat(selected.target()).isEqualByComparingTo("20");
        assertThat(selected.tolerance()).isEqualByComparingTo("2");
        assertThat(selected.version()).isEqualTo("v1");
        assertThat(selected.responseTime()).isEqualTo(Duration.ofSeconds(60));
        plan.setTargetClimateMetrics(Map.of());
        assertThatThrownBy(() -> com.seasonplan.monitoring.domain.MetricRulePolicy.select(seasonFacts(plan), MetricDefinition.TEMPERATURE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("target or rule");
    }

    private static com.seasonplan.pollen.api.ReservationFacts reservationFacts(PollenReservation value) {
        return new com.seasonplan.pollen.api.ReservationFacts(value.id(), value.seasonPlanId(), value.requestedAmount(), value.confirmedAmount(), value.status(), value.comment(), value.confirmedBy(), 1L, false);
    }
    private static com.seasonplan.work.api.TaskFacts taskFacts(MagicTask task) {
        return new com.seasonplan.work.api.TaskFacts(task.id(), task.seasonPlanId(), task.title(), task.description(),
                task.requiredTalent(), task.timeWindow(), task.status(), task.category());
    }
    private static com.seasonplan.work.api.AssignmentFacts assignmentFacts(FairyAssignment assignment) {
        return new com.seasonplan.work.api.AssignmentFacts(assignment.id(), assignment.magicTaskId(), assignment.fairyId(),
                assignment.role(), assignment.talent(), assignment.status(), assignment.confirmedAt());
    }

    private static com.seasonplan.season.api.SeasonFacts seasonFacts(SeasonPlan plan) {
        return new com.seasonplan.season.api.SeasonFacts(plan.id(), plan.seasonType(), plan.targetTerritories(), plan.timeWindows(),
                plan.naturalPhenomenonParameters(), plan.targetClimateMetrics(), plan.status(), plan.approvalComment(),
                plan.readinessChecklist().stream().map(item -> new com.seasonplan.season.api.SeasonFacts.ChecklistFact(item.key(), item.complete())).toList(),
                plan.createdBy(), plan.updatedBy(), plan.createdAt(), plan.updatedAt(), plan.approvalConditions(), plan.executionRules(), plan.materialRevision());
    }

    private static SeasonPlan plan(String territory) {
        return SeasonPlan.createDraft(UUID.randomUUID(), "SPRING", List.of(territory), List.of(WINDOW), Map.of("rain", "normal"),
                Map.of("temperature", 20.0, "humidity", 60.0, "pollen_density", 1.0), List.of(new ChecklistItem("reviewed", true)), "planner", NOW);
    }
    private static MagicTask task(MagicTaskStatus status) {
        var task = new MagicTask("Water", "", "WATER", WINDOW, status, "WEATHER"); task.setId(UUID.randomUUID()); task.setSeasonPlanId(UUID.randomUUID()); return task;
    }
    private static ExecutionRuleSnapshot rules() {
        var metric = new ExecutionRuleSnapshot.MetricSettings(new BigDecimal("2"), "owner", 60);
        var season = new ExecutionRuleSnapshot.SeasonRequirements(Set.of("WEATHER"), Set.of("LEAD"));
        return new ExecutionRuleSnapshot("v1", Map.of("temperature", metric, "humidity", metric, "pollen_density", metric), Set.of("LAUNCHED"),
                Map.of("SPRING", season, "SUMMER", season, "AUTUMN", season, "WINTER", season));
    }
}
