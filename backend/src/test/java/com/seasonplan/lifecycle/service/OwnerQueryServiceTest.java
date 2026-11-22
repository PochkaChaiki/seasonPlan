package com.seasonplan.lifecycle.service;

import com.seasonplan.work.application.WorkQueryService;
import com.seasonplan.work.domain.*;
import com.seasonplan.work.application.port.*;
import com.seasonplan.response.application.ResponseQueryService;
import com.seasonplan.ritual.application.RitualQueryService;
import com.seasonplan.monitoring.application.MetricQueryService;
import com.seasonplan.ritual.domain.RitualSession;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerQueryServiceTest {
    @Test void workQueriesDetachMutableTasksAssignmentsAndRepositoryLists() {
        UUID plan = UUID.randomUUID(), taskId = UUID.randomUUID();
        var task = new MagicTask("title", "description", "talent", null, MagicTaskStatus.READY, "WEATHER");
        task.setId(taskId); task.setSeasonPlanId(plan);
        var taskRows = new ArrayList<>(List.of(task));
        var assignment = mock(FairyAssignment.class);
        when(assignment.id()).thenReturn(UUID.randomUUID()); when(assignment.magicTaskId()).thenReturn(taskId);
        when(assignment.role()).thenReturn("LEAD"); when(assignment.status()).thenReturn(AssignmentStatus.CONFIRMED);
        when(assignment.confirmedAt()).thenReturn(Instant.EPOCH);
        var assignmentRows = new ArrayList<>(List.of(assignment));
        var tasks = mock(MagicTaskRepository.class); var assignments = mock(FairyAssignmentRepository.class);
        when(tasks.findBySeasonPlanId(plan)).thenReturn(taskRows); when(assignments.findByMagicTaskId(taskId)).thenReturn(assignmentRows);
        var queries = new WorkQueryService(tasks, assignments);
        var facts = queries.tasksForPlan(plan); var attached = queries.assignmentsForTask(taskId);
        task.updateFrom(new MagicTask("changed", "", "other", null, MagicTaskStatus.TODO, "POLLEN"));
        when(assignment.role()).thenReturn("OTHER"); taskRows.clear(); assignmentRows.clear();
        assertThat(facts.get(0).title()).isEqualTo("title"); assertThat(facts.get(0).category()).isEqualTo("WEATHER");
        assertThat(facts.get(0).id()).isEqualTo(taskId); assertThat(facts.get(0).seasonPlanId()).isEqualTo(plan);
        assertThat(facts.get(0).requiredTalent()).isEqualTo("talent"); assertThat(facts.get(0).description()).isEqualTo("description");
        assertThat(attached.get(0).role()).isEqualTo("LEAD"); assertThat(attached.get(0).confirmedAt()).isEqualTo(Instant.EPOCH);
        assertThat(attached.get(0).getClass().isRecord()).isTrue(); assertThat(facts.get(0).getClass().isRecord()).isTrue();
        assertThatThrownBy(facts::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(attached::clear).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void responseAndMetricQueriesFreezeListsOfSupportedImmutableDomainValues() {
        var f = new ExecutionTestFixture(com.seasonplan.season.domain.SeasonPlanStatus.ACTIVE);
        f.successfulSources();
        var incident = new com.seasonplan.response.domain.MagicIncident(UUID.randomUUID(), f.plan.id(), com.seasonplan.response.domain.MagicIncident.Type.CLIMATE,
                com.seasonplan.response.domain.MagicIncident.Severity.CRITICAL, com.seasonplan.response.domain.MagicIncident.Status.OPEN, "cause", "impact", "actor", f.now);
        f.incidents.put(incident.id(), incident);
        var action = com.seasonplan.response.domain.CorrectionAction.open(UUID.randomUUID(), f.plan.id(), incident.id(), "key", "description", "owner", f.now);
        f.actions.put(action.id(), action);
        var incidentRows = new ArrayList<>(List.of(incident));
        var actionRows = new ArrayList<>(List.of(action));
        var metricRows = new ArrayList<>(f.metricPort.forPlan(f.plan.id()));
        var incidentPort = mock(com.seasonplan.response.application.port.MagicIncidentRepository.class);
        var actionPort = mock(com.seasonplan.response.application.port.CorrectionActionRepository.class);
        var metricPort = mock(com.seasonplan.monitoring.application.port.ClimateMetricRepository.class);
        when(incidentPort.forPlan(f.plan.id())).thenReturn(incidentRows);
        when(actionPort.forPlan(f.plan.id())).thenReturn(actionRows);
        when(metricPort.forPlan(f.plan.id())).thenReturn(metricRows);
        var response = new ResponseQueryService(incidentPort, actionPort);
        var incidents = response.incidentsForPlan(f.plan.id()); var actions = response.correctionsForPlan(f.plan.id());
        var metrics = new MetricQueryService(metricPort).forPlan(f.plan.id());
        incidentRows.clear(); actionRows.clear(); metricRows.clear();
        assertThat(incidents).containsExactly(incident); assertThat(actions).containsExactly(action); assertThat(metrics).hasSize(3);
        assertThat(incident.resolve()).isNotEqualTo(incidents.get(0));
        assertThat(actions.get(0).status()).isEqualTo(com.seasonplan.response.domain.CorrectionAction.Status.OPEN);
        assertThatThrownBy(incidents::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(actions::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(metrics::clear).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void ritualQueriesRetainDeeplyImmutableParticipantsAndEvents() {
        var f = new ExecutionTestFixture(com.seasonplan.season.domain.SeasonPlanStatus.RITUAL_READY);
        var participants = new ArrayList<>(List.of(new RitualSession.Participant("fairy", "LEAD", f.now)));
        var events = new ArrayList<>(List.of(new RitualSession.Event(f.now, "LAUNCH_VALIDATED", "payload")));
        var session = new RitualSession(UUID.randomUUID(), f.plan.id(), 1, "actor", f.now, null, participants, events, null, null, null);
        f.sessions.put(session.id(), session);
        var result = new RitualQueryService(f.ritualPort).forPlan(f.plan.id());
        participants.clear(); events.clear(); f.sessions.clear();
        assertThat(result).hasSize(1); assertThat(result.get(0).participants()).hasSize(1); assertThat(result.get(0).events()).hasSize(1);
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.get(0).participants().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.get(0).events().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void supportedPublicValuesHaveOnlyDeeplyImmutableComponents() {
        for (Class<?> type : List.of(com.seasonplan.work.api.TaskFacts.class, com.seasonplan.work.api.AssignmentFacts.class,
                com.seasonplan.response.domain.MagicIncident.class, com.seasonplan.response.domain.CorrectionAction.class,
                RitualSession.class, com.seasonplan.monitoring.domain.ClimateMetric.class)) assertImmutableValue(type);
    }
    private void assertImmutableValue(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || List.of(String.class, UUID.class, Instant.class,
                java.time.LocalDateTime.class, java.math.BigDecimal.class).contains(type)) return;
        assertThat(type.isRecord()).as(type.getName()).isTrue();
        for (var component : type.getRecordComponents()) {
            if (component.getType() == List.class) {
                var generic = (java.lang.reflect.ParameterizedType) component.getGenericType();
                assertImmutableValue((Class<?>) generic.getActualTypeArguments()[0]);
            } else assertImmutableValue(component.getType());
        }
    }
}
