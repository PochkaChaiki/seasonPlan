package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.AssignmentStatus;
import com.seasonplan.lifecycle.domain.MagicTask;
import com.seasonplan.lifecycle.domain.PollenReservation;
import com.seasonplan.lifecycle.domain.SeasonPlan;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;
import com.seasonplan.lifecycle.application.preparation.FairyAssignmentService;
import com.seasonplan.lifecycle.application.preparation.MagicTaskService;
import com.seasonplan.lifecycle.application.pollen.PollenService;
import com.seasonplan.lifecycle.application.preparation.ReadinessChecklistService;
import com.seasonplan.lifecycle.application.port.SeasonPlanRepository;
import com.seasonplan.lifecycle.service.SeasonPlanService;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.seasonplan.lifecycle.application.port.PollenTreeClient;
import com.seasonplan.lifecycle.domain.AuditLogEntry;
import com.seasonplan.lifecycle.domain.PollenReservationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PostgresLifecyclePersistenceTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("seasonplan_test")
            .withUsername("seasonplan")
            .withPassword("seasonplan_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SeasonPlanService seasonPlanService;

    @Autowired
    private PollenService pollenService;

    @Autowired
    private MagicTaskService magicTaskService;

    @Autowired
    private FairyAssignmentService fairyAssignmentService;

    @Autowired
    private ReadinessChecklistService readinessChecklistService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SeasonPlanRepository seasonPlanRepository;

    @Autowired
    private PollenTreeClient pollenTree;

    @SpyBean
    private JdbcAuditLogRepository auditRepository;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void externalDecisionsNeverPersistReservationsAndAuditFailureCanBeRetried(boolean confirm) {
        SeasonPlan plan = seasonPlanService.createDraft(SeasonPlanTestFixture.validPlanDraft("planner-1"),
                ActorRole.FAIRY_PLANNER, "planner-1");
        PollenReservation reservation = pollenService.requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        // Model a persistence failure directly: Spring translates generic IllegalStateException at @Repository boundaries.
        var failure = new org.springframework.dao.DataAccessResourceFailureException("simulated audit failure");
        doThrow(failure).doCallRealMethod().when(auditRepository).append(any(AuditLogEntry.class));
        assertThatThrownBy(() -> decide(reservation.id(), confirm)).isSameAs(failure);
        assertThat(pollenTree.findById(reservation.id()).orElseThrow().status())
                .isEqualTo(confirm ? PollenReservationStatus.CONFIRMED : PollenReservationStatus.REJECTED);
        decide(reservation.id(), confirm);
        decide(reservation.id(), confirm);
        assertThat(jdbc.queryForObject("select count(*) from pollen_reservations where season_plan_id = ?", Integer.class, plan.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ? and action like 'POLLEN_RESERVATION_%'",
                Integer.class, plan.id())).isEqualTo(1);
    }

    private void decide(UUID id, boolean confirm) {
        if (confirm) pollenService.confirmReservation(id, 100, "decision", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        else pollenService.rejectReservation(id, "decision", ActorRole.POLLEN_GUARDIAN, "pollen-1");
    }

    @Test
    void lifecycleServicesPersistSeasonPlanAndAuditLogToPostgres() {
        SeasonPlan draft = seasonPlanService.createDraft(
                SeasonPlanTestFixture.validPlanDraft("planner-1"),
                ActorRole.FAIRY_PLANNER,
                "planner-1"
        );

        assertThat(jdbc.queryForObject("select count(*) from season_plans where id = ?", Integer.class, draft.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from season_plan_time_windows where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from season_plan_target_territories where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from readiness_checklist_items where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(1);

        SeasonPlan submitted = seasonPlanService.submitForApproval(draft.id(), ActorRole.FAIRY_PLANNER, "planner-1");

        assertThat(submitted.status()).isEqualTo(SeasonPlanStatus.SUBMITTED);
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, draft.id()))
                .isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where season_plan_id = ?", Integer.class, draft.id()))
                .isEqualTo(2);
    }

    @Test
    void preparationDataIsPersistedAndLoadedFromPostgres() {
        SeasonPlan draft = seasonPlanService.createDraft(
                SeasonPlanTestFixture.validPlanDraft("planner-1"),
                ActorRole.FAIRY_PLANNER,
                "planner-1"
        );
        seasonPlanService.submitForApproval(draft.id(), ActorRole.FAIRY_PLANNER, "planner-1");
        PollenReservation reservation = pollenService.requestReservation(draft.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen-1");
        pollenService.confirmReservation(reservation.id(), 100, "within limit", ActorRole.POLLEN_GUARDIAN, "pollen-1");
        SeasonPlan approved = seasonPlanService.approve(draft.id(), "approved", ActorRole.CHIEF_GUARDIAN, "guardian-1");
        MagicTask task = magicTaskService.createTask(approved.id(), SeasonPlanTestFixture.validTaskDraft(), ActorRole.TALENT_COORDINATOR, "coordinator-1");
        fairyAssignmentService.assignFairy(task.id(), "fairy-1", "LEAD", "SPRING_WEATHER", AssignmentStatus.CONFIRMED, ActorRole.TALENT_COORDINATOR, "coordinator-1");
        readinessChecklistService.completeItem(approved.id(), "calendar-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");
        readinessChecklistService.completeItem(approved.id(), "roles-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");

        SeasonPlan ready = seasonPlanService.markRitualReady(approved.id(), ActorRole.TALENT_COORDINATOR, "coordinator-1");

        assertThat(ready.status()).isEqualTo(SeasonPlanStatus.RITUAL_READY);
        assertThat(pollenService.get(reservation.id()).status()).isEqualTo(PollenReservationStatus.CONFIRMED);
        assertThat(jdbc.queryForObject("select count(*) from pollen_reservations where season_plan_id = ?", Integer.class, approved.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from magic_tasks where season_plan_id = ?", Integer.class, approved.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from fairy_assignments where magic_task_id = ?", Integer.class, task.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from readiness_checklist_items where season_plan_id = ? and complete = true", Integer.class, approved.id()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from season_plans where id = ?", String.class, approved.id()))
                .isEqualTo("RITUAL_READY");
    }

    @Test
    void completingChecklistItemsPreservesExistingCompletionMetadata() {
        SeasonPlan draft = seasonPlanService.createDraft(
                SeasonPlanTestFixture.validPlanDraft("planner-1"),
                ActorRole.FAIRY_PLANNER,
                "planner-1"
        );

        readinessChecklistService.completeItem(draft.id(), "calendar-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-1");
        Map<String, Object> firstCompletion = checklistRow(draft.id(), "calendar-reviewed");

        readinessChecklistService.completeItem(draft.id(), "roles-reviewed", ActorRole.TALENT_COORDINATOR, "coordinator-2");
        Map<String, Object> afterUnrelatedCompletion = checklistRow(draft.id(), "calendar-reviewed");

        assertThat(afterUnrelatedCompletion.get("id")).isEqualTo(firstCompletion.get("id"));
        assertThat(afterUnrelatedCompletion.get("completed_by")).isEqualTo("coordinator-1");
        assertThat(afterUnrelatedCompletion.get("completed_at")).isEqualTo(firstCompletion.get("completed_at"));
    }

    @Test
    void repositoryListsPersistedSeasonPlansForSelection() {
        SeasonPlan spring = seasonPlanService.createDraft(
                SeasonPlanTestFixture.validPlanDraft("planner-1"),
                ActorRole.FAIRY_PLANNER,
                "planner-1"
        );
        SeasonPlan summerDraft = SeasonPlanTestFixture.validPlanDraft("planner-1");
        summerDraft.setSeasonType("SUMMER");
        SeasonPlan summer = seasonPlanService.createDraft(summerDraft, ActorRole.FAIRY_PLANNER, "planner-1");

        assertThat(seasonPlanRepository.findAll())
                .extracting(SeasonPlan::id)
                .contains(spring.id(), summer.id());
    }

    private Map<String, Object> checklistRow(UUID seasonPlanId, String itemKey) {
        return jdbc.queryForMap("""
                select id, completed_by, completed_at
                  from readiness_checklist_items
                 where season_plan_id = ? and item_key = ?
                """, seasonPlanId, itemKey);
    }
}
