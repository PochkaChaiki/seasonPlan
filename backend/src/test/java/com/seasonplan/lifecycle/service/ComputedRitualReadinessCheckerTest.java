package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.*;
import com.seasonplan.lifecycle.infrastructure.fake.*;
import com.seasonplan.lifecycle.repository.MagicIncidentRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComputedRitualReadinessCheckerTest {
    @Test void requiresCompletedCategoriesCompatibleConfirmedRolesPollenAndNoCriticalIncident() {
        var plans = new InMemorySeasonPlanRepository(); var tasks = new InMemoryMagicTaskRepository();
        var assignments = new InMemoryFairyAssignmentRepository(); var pollen = mock(PollenService.class); var incidents = mock(MagicIncidentRepository.class);
        UUID id = UUID.randomUUID(); Instant now = Instant.parse("2026-01-01T00:00:00Z");
        SeasonPlan plan = SeasonPlan.rehydrate(id, "SPRING", List.of("north"), List.of(), Map.of(), Map.of(), SeasonPlanStatus.APPROVED, null, List.of(new ChecklistItem("manual", true)), "a", "a", now, now);
        plan.pinExecutionRules(TestExecutionRules.snapshot()); plans.save(plan);
        PollenReservation reservation = new PollenReservation(UUID.randomUUID(), id, 10); reservation.confirm(10, "", "guardian");
        when(pollen.findLatestBySeasonPlanId(id)).thenReturn(Optional.of(reservation));
        when(pollen.findAuthoritativeConfirmedBySeasonPlanId(id)).thenReturn(Optional.of(reservation));
        when(incidents.forPlan(id)).thenReturn(List.of());
        MagicTask weather = new MagicTask("weather", "", "talent", null, MagicTaskStatus.READY, "WEATHER"); weather.setId(UUID.randomUUID()); weather.setSeasonPlanId(id); tasks.save(weather, "a");
        var checker = new ComputedRitualReadinessChecker(plans, tasks, assignments, pollen, incidents);
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
}
