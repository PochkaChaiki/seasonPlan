package com.seasonplan.lifecycle.service;

import com.seasonplan.season.api.SeasonFacts;
import com.seasonplan.season.application.SeasonQueryService;
import com.seasonplan.season.adapter.out.fake.InMemorySeasonPlanRepository;
import com.seasonplan.season.domain.*;
import com.seasonplan.shared.application.NotFoundException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SeasonQueryServiceTest {
    @Test void queriesSnapshotEveryPersistedFieldAndDetachMutableChecklistElements() {
        var plans = new InMemorySeasonPlanRepository();
        var item = new ChecklistItem("manual", false);
        var condition = new ApprovalCondition("condition", "text", "evidence", com.seasonplan.identity.api.ActorRole.CHIEF_GUARDIAN);
        var plan = SeasonPlan.rehydrate(UUID.randomUUID(), "SPRING", List.of("north"), List.of(), Map.of("bloom", "early"), Map.of("temperature", 20.0),
                SeasonPlanStatus.APPROVED, "approved", List.of(item), "creator", "updater", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), List.of(condition), new com.seasonplan.season.domain.MaterialRevisionStamp(0, "system:migration", java.time.Instant.EPOCH, com.seasonplan.season.domain.MaterialRevisionStamp.Reason.LEGACY_BASELINE));
        plan.pinExecutionRules(TestExecutionRules.snapshot()); plans.save(plan);
        var queries = new SeasonQueryService(plans);
        var facts = queries.get(plan.id());
        assertThat(facts.id()).isEqualTo(plan.id());
        assertThat(facts.status()).isEqualTo(plan.status());
        assertThat(facts.seasonType()).isEqualTo(plan.seasonType());
        assertThat(facts.createdBy()).isEqualTo("creator"); assertThat(facts.updatedBy()).isEqualTo("updater");
        assertThat(facts.createdAt()).isEqualTo(plan.createdAt()); assertThat(facts.updatedAt()).isEqualTo(plan.updatedAt());
        assertThat(facts.approvalComment()).isEqualTo("approved");
        assertThat(facts.approvalConditions()).containsExactly(condition);
        assertThat(facts.executionRules()).isEqualTo(plan.executionRules());
        item.markComplete(); plan.setTargetTerritories(List.of("south"));
        assertThat(facts.readinessChecklist()).containsExactly(new SeasonFacts.ChecklistFact("manual", false));
        assertThat(facts.targetTerritories()).containsExactly("north");
        assertThatThrownBy(() -> facts.readinessChecklist().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facts.targetTerritories().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facts.timeWindows().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facts.naturalPhenomenonParameters().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facts.targetClimateMetrics().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facts.approvalConditions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facts.executionRules().seasons().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> queries.listPlans().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(plan.targetTerritories()).containsExactly("south");
        var current = plans.findById(plan.id()).orElseThrow(); current.completeReadinessChecklistItem("manual", "other", Instant.EPOCH.plusSeconds(2)); plans.save(current);
        assertThat(queries.get(plan.id()).readinessChecklist().get(0).complete()).isTrue();
    }
    @Test void factsCopyCallerCollectionsWithoutChangingInvalidDraftValues() {
        var territories = new ArrayList<>(Arrays.asList("north", null));
        var windows = new ArrayList<com.seasonplan.shared.domain.TimeWindow>();
        var parameters = new HashMap<>(Map.of("bloom", "early"));
        var targets = new HashMap<String, Double>(); targets.put("temperature", null);
        var checklist = new ArrayList<>(List.of(new SeasonFacts.ChecklistFact("manual", false)));
        var conditions = new ArrayList<>(List.of(new ApprovalCondition("condition", "text", "evidence", com.seasonplan.identity.api.ActorRole.CHIEF_GUARDIAN)));
        var facts = new SeasonFacts(UUID.randomUUID(), "SPRING", territories, windows, parameters, targets, SeasonPlanStatus.DRAFT,
                null, checklist, "creator", "creator", Instant.EPOCH, Instant.EPOCH, conditions, null, 0L);
        territories.clear(); windows.add(new com.seasonplan.shared.domain.TimeWindow(null, null)); parameters.clear(); targets.clear(); checklist.clear(); conditions.clear();
        assertThat(facts.targetTerritories()).containsExactly("north", null);
        assertThat(facts.timeWindows()).isEmpty();
        assertThat(facts.naturalPhenomenonParameters()).containsEntry("bloom", "early");
        assertThat(facts.targetClimateMetrics()).containsEntry("temperature", null);
        assertThat(facts.readinessChecklist()).hasSize(1);
        assertThat(facts.approvalConditions()).hasSize(1);
    }
    @Test void missingSeasonUsesExistingNotFoundSemantics() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> new SeasonQueryService(new InMemorySeasonPlanRepository()).get(id))
                .isInstanceOf(NotFoundException.class).hasMessage("season plan not found: " + id);
    }
}
