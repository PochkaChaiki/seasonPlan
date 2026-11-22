package com.seasonplan.lifecycle.service;

import com.seasonplan.season.adapter.out.fake.InMemorySeasonPlanRepository;
import com.seasonplan.season.application.ServiceSupport;
import com.seasonplan.season.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MaterialRevisionHistoryTest {
    @Test void appendSnapshotsRejectSameRevisionRewritesAndKeepOriginalAuthor() {
        var repository = new InMemorySeasonPlanRepository();
        var plan = SeasonPlan.createDraft(UUID.randomUUID(), "SPRING", List.of("north"), List.of(), Map.of("bloom", "early"), Map.of("temperature", 20.0), List.of(), "creator", Instant.EPOCH);
        plan.pinExecutionRules(TestExecutionRules.snapshot()); repository.save(plan);
        var first = repository.materialHistory(plan.id()).get(0);
        var accidental = repository.findById(plan.id()).orElseThrow(); accidental.setTargetClimateMetrics(Map.of("temperature", 21.0));
        assertThatThrownBy(() -> repository.save(accidental)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(plan.id()).orElseThrow().targetClimateMetrics()).containsEntry("temperature", 20.0);
        var edited = repository.findById(plan.id()).orElseThrow(); var draft = new SeasonPlan();
        draft.setSeasonType(edited.seasonType()); draft.setTargetTerritories(edited.targetTerritories()); draft.setTimeWindows(edited.timeWindows());
        draft.setNaturalPhenomenonParameters(edited.naturalPhenomenonParameters()); draft.setTargetClimateMetrics(Map.of("temperature", 22.0));
        edited.updateDraftFieldsFrom(draft, "editor", Instant.EPOCH.plusSeconds(1)); repository.save(edited);
        var metadata = ServiceSupport.copy(edited); metadata.completeReadinessChecklistItem("manual", "coordinator", Instant.EPOCH.plusSeconds(2)); repository.save(metadata);
        assertThat(repository.materialHistory(plan.id())).hasSize(2);
        assertThat(repository.materialHistory(plan.id()).get(0)).isEqualTo(first);
        assertThat(first.executionRules()).isEqualTo(plan.executionRules());
        assertThat(first.stamp().actorId()).isEqualTo("creator");
        assertThat(first.material().targetClimateMetrics()).containsEntry("temperature", 20.0);
        assertThat(metadata.materialRevision()).isEqualTo(2);
        assertThatThrownBy(() -> repository.materialHistory(plan.id()).clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
