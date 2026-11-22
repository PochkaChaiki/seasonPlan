package com.seasonplan.lifecycle.service;

import com.seasonplan.pollen.application.PollenService;
import com.seasonplan.pollen.domain.PollenReservation;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.season.adapter.out.fake.InMemorySeasonPlanRepository;
import com.seasonplan.season.domain.ChecklistItem;
import com.seasonplan.season.domain.SeasonPlan;
import com.seasonplan.season.domain.SeasonPlanStatus;
import com.seasonplan.shared.application.LegacyExecutionMetadataException;
import com.seasonplan.shared.domain.DomainValidationException;
import com.seasonplan.work.adapter.out.fake.InMemoryFairyAssignmentRepository;
import com.seasonplan.work.adapter.out.fake.InMemoryMagicTaskRepository;
import com.seasonplan.work.domain.AssignmentStatus;
import com.seasonplan.work.domain.FairyAssignment;
import com.seasonplan.work.domain.MagicTask;
import com.seasonplan.work.domain.MagicTaskStatus;
import com.seasonplan.workflows.application.ComputedRitualReadinessChecker;

import com.seasonplan.response.application.port.MagicIncidentRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComputedRitualReadinessCheckerTest {
    @Test void requiresCompletedCategoriesCompatibleConfirmedRolesPollenAndNoCriticalIncident() {
        var plans = new InMemorySeasonPlanRepository(); var tasks = new InMemoryMagicTaskRepository();
        var assignments = new InMemoryFairyAssignmentRepository(); var pollen = mock(com.seasonplan.pollen.api.PollenOperations.class); var incidents = mock(MagicIncidentRepository.class);
        UUID id = UUID.randomUUID(); Instant now = Instant.parse("2026-01-01T00:00:00Z");
        SeasonPlan plan = SeasonPlan.rehydrate(id, "SPRING", List.of("north"), List.of(), Map.of(), Map.of(), SeasonPlanStatus.APPROVED, null, List.of(new ChecklistItem("manual", true)), "a", "a", now, now, new com.seasonplan.season.domain.MaterialRevisionStamp(0, "system:migration", java.time.Instant.EPOCH, com.seasonplan.season.domain.MaterialRevisionStamp.Reason.LEGACY_BASELINE));
        plan.pinExecutionRules(TestExecutionRules.snapshot()); plans.save(plan);
        PollenReservation reservation = new PollenReservation(UUID.randomUUID(), id, 10); reservation.confirm(10, "", "guardian");
        when(pollen.findLatestBySeasonPlanId(id)).thenReturn(Optional.of(reservationFacts(reservation, plan.materialRevision())));
        when(pollen.findAuthoritativeConfirmedBySeasonPlanId(id)).thenReturn(Optional.of(reservationFacts(reservation, plan.materialRevision())));
        when(incidents.forPlan(id)).thenReturn(List.of());
        MagicTask weather = new MagicTask("weather", "", "talent", null, MagicTaskStatus.READY, "WEATHER"); weather.setId(UUID.randomUUID()); weather.setSeasonPlanId(id); tasks.save(weather, "a");
        var checker = new ComputedRitualReadinessChecker(new com.seasonplan.season.application.SeasonQueryService(plans), new com.seasonplan.work.application.WorkQueryService(tasks, assignments), pollen,
                new com.seasonplan.response.application.ResponseQueryService(incidents, mock(com.seasonplan.response.application.port.CorrectionActionRepository.class)), new com.seasonplan.ritual.domain.RitualReadinessPolicy());
        assertThat(checker.inspect(id).blockers()).contains("category:POLLEN", "task:" + weather.id(), "role:RITUAL_LEAD", "role:WEATHER_WEAVER");
        weather.updateFrom(new MagicTask("weather", "", "talent", null, MagicTaskStatus.COMPLETED, "WEATHER"));
        MagicTask pollenTask = new MagicTask("pollen", "", "talent", null, MagicTaskStatus.COMPLETED, "POLLEN"); pollenTask.setId(UUID.randomUUID()); pollenTask.setSeasonPlanId(id); tasks.save(pollenTask, "a");
        assignments.save(new FairyAssignment(UUID.randomUUID(), pollenTask.id(), "lead", "RITUAL_LEAD", "talent", AssignmentStatus.CONFIRMED, now), "a");
        assignments.save(new FairyAssignment(UUID.randomUUID(), pollenTask.id(), "weaver", "WEATHER_WEAVER", "talent", AssignmentStatus.CONFIRMED, now), "a");
        assertThat(checker.inspect(id).blockers()).contains("assignment:" + weather.id());
        assignments.save(new FairyAssignment(UUID.randomUUID(), weather.id(), "legacy", "OTHER", "talent", AssignmentStatus.CONFIRMED), "a");
        assignments.save(new FairyAssignment(UUID.randomUUID(), weather.id(), "wrong", "OTHER", "wrong-talent", AssignmentStatus.CONFIRMED, now), "a");
        assertThat(checker.inspect(id).blockers()).contains("assignment:" + weather.id());
        assignments.save(new FairyAssignment(UUID.randomUUID(), weather.id(), "worker", "OTHER", "talent", AssignmentStatus.CONFIRMED, now), "a");
        MagicTask optional = new MagicTask("optional", "", "talent", null, MagicTaskStatus.TODO, "WATER"); optional.setId(UUID.randomUUID()); optional.setSeasonPlanId(id); tasks.save(optional, "a");
        assertThat(checker.inspect(id).blockers()).isEmpty();
        assertThat(checker.inspect(id).participants()).hasSize(3);
        MagicTask unknown = new MagicTask("unknown", "", "talent", null, MagicTaskStatus.TODO, null); unknown.setId(UUID.randomUUID()); unknown.setSeasonPlanId(id); tasks.save(unknown, "a");
        assertThat(checker.inspect(id).blockers()).contains("task-category:" + unknown.id());
        when(incidents.forPlan(id)).thenReturn(List.of(new MagicIncident(UUID.randomUUID(), id, MagicIncident.Type.CLIMATE, MagicIncident.Severity.CRITICAL, MagicIncident.Status.OPEN, "cause", "impact", "actor", now)));
        assertThat(checker.inspect(id).blockers()).contains("critical-incident");
        when(incidents.forPlan(id)).thenThrow(new LegacyExecutionMetadataException("magic_incidents"));
        assertThatThrownBy(() -> checker.inspect(id)).isInstanceOfSatisfying(DomainValidationException.class,
                ex -> assertThat(ex.fieldErrors()).containsKey("executionRules"));
    }
    @Test void publicFactCollectionRetainsResourceWorkAssignmentAndIncidentLoadOrder() {
        var f = new ExecutionTestFixture(SeasonPlanStatus.RITUAL_READY);
        UUID id = f.plan.id();
        var seasons = mock(com.seasonplan.season.api.SeasonQueries.class);
        var work = mock(com.seasonplan.work.api.WorkQueries.class);
        var pollen = mock(com.seasonplan.pollen.api.PollenOperations.class);
        var response = mock(com.seasonplan.response.api.ResponseQueries.class);
        when(seasons.get(id)).thenReturn(new com.seasonplan.season.application.SeasonQueryService(f.plans).get(id));
        var reservation = new PollenReservation(UUID.randomUUID(), id, 100); reservation.confirm(100, "ok", "guardian");
        when(pollen.findLatestBySeasonPlanId(id)).thenReturn(Optional.of(reservationFacts(reservation, f.plan.materialRevision())));
        when(pollen.findAuthoritativeConfirmedBySeasonPlanId(id)).thenReturn(Optional.of(reservationFacts(reservation, f.plan.materialRevision())));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(work.tasksForPlan(id)).thenReturn(List.of(
                new com.seasonplan.work.api.TaskFacts(first, id, "first", "", "talent", null, MagicTaskStatus.COMPLETED, "WEATHER"),
                new com.seasonplan.work.api.TaskFacts(second, id, "second", "", "talent", null, MagicTaskStatus.COMPLETED, "POLLEN")));
        new ComputedRitualReadinessChecker(seasons, work, pollen, response, new com.seasonplan.ritual.domain.RitualReadinessPolicy()).inspect(id);
        var order = inOrder(seasons, pollen, work, response);
        order.verify(seasons).get(id);
        order.verify(pollen).findLatestBySeasonPlanId(id);
        order.verify(pollen).findAuthoritativeConfirmedBySeasonPlanId(id);
        order.verify(work).tasksForPlan(id);
        order.verify(work).assignmentsForTask(first);
        order.verify(work).assignmentsForTask(second);
        order.verify(response).incidentsForPlan(id);
        order.verifyNoMoreInteractions();
    }
    private static com.seasonplan.pollen.api.ReservationFacts reservationFacts(PollenReservation value, long revision) {
        return new com.seasonplan.pollen.api.ReservationFacts(value.id(), value.seasonPlanId(), value.requestedAmount(), value.confirmedAmount(), value.status(), value.comment(), value.confirmedBy(), revision, false);
    }
}
