package com.seasonplan.lifecycle.domain;

import com.seasonplan.season.domain.*;
import com.seasonplan.shared.domain.TimeWindow;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MaterialRevisionTest {
    private SeasonPlan plan() {
        return SeasonPlan.createDraft(UUID.randomUUID(), "SPRING", List.of("south", "north"), List.of(new TimeWindow(LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 10, 0))), Map.of("bloom", "early"), Map.of("temperature", 20.0), List.of(), "creator", Instant.EPOCH);
    }
    @Test void materialChangesAdvanceMonotonicallyButMetadataAndValueEqualEditsDoNot() {
        var plan = plan();
        assertThat(plan.materialRevision()).isEqualTo(1);
        var original = plan.materialSnapshot();
        var equal = plan(); equal.setTargetTerritories(List.of("north", "south"));
        plan.updateDraftFieldsFrom(equal, "editor", Instant.EPOCH.plusSeconds(1));
        assertThat(plan.materialRevision()).isEqualTo(1);
        assertThat(plan.materialRevisionStamp().actorId()).isEqualTo("creator");
        plan.completeReadinessChecklistItem("manual", "coordinator", Instant.EPOCH.plusSeconds(2));
        assertThat(plan.materialRevision()).isEqualTo(1);
        for (int field = 0; field < 5; field++) {
            var draft = plan();
            if (field == 0) draft.setSeasonType("SUMMER");
            if (field == 1) draft.setTargetTerritories(List.of("west"));
            if (field == 2) draft.setTimeWindows(List.of(new TimeWindow(equal.timeWindows().get(0).start(), equal.timeWindows().get(0).end().plusMinutes(1))));
            if (field == 3) draft.setNaturalPhenomenonParameters(Map.of("bloom", "late"));
            if (field == 4) draft.setTargetClimateMetrics(Map.of("temperature", 21.0));
            var isolated = plan(); isolated.updateDraftFieldsFrom(draft, "editor", Instant.EPOCH.plusSeconds(field + 3));
            assertThat(isolated.materialRevision()).as("material field " + field).isEqualTo(2);
            long previous = plan.materialRevision();
            plan.updateDraftFieldsFrom(draft, "editor", Instant.EPOCH.plusSeconds(field + 3));
            assertThat(plan.materialRevision()).isEqualTo(previous + 1);
            assertThat(plan.materialRevisionStamp().reason()).isEqualTo(MaterialRevisionStamp.Reason.EDIT);
        }
        assertThat(original.targetTerritories()).containsExactly("north", "south");
        assertThat(original.targetClimateMetrics()).containsEntry("temperature", 20.0);
        assertThatThrownBy(() -> original.targetClimateMetrics().clear()).isInstanceOf(UnsupportedOperationException.class);
        long revision = plan.materialRevision(); plan.submit("editor", Instant.EPOCH.plusSeconds(20)); plan.verify("chief", Instant.EPOCH.plusSeconds(21));
        plan.returnForRevision("revise", "chief", Instant.EPOCH.plusSeconds(22));
        assertThat(plan.materialRevision()).isEqualTo(revision);
    }
}
