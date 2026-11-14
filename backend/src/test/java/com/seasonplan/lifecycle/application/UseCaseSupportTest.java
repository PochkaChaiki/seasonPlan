package com.seasonplan.lifecycle.application;

import com.seasonplan.lifecycle.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UseCaseSupportTest {
    @Test void copyPreservesApprovalConditions() {
        var condition = new ApprovalCondition("weather", "Weather", "Evidence", ActorRole.TALENT_COORDINATOR);
        var plan = SeasonPlan.rehydrate(UUID.randomUUID(), "SPRING", List.of("north"), List.of(), Map.of(), Map.of(),
                SeasonPlanStatus.APPROVED, "conditional", List.of(), "planner", "chief", Instant.EPOCH, Instant.EPOCH, List.of(condition));
        assertThat(UseCaseSupport.copy(plan).approvalConditions()).containsExactly(condition);
    }
}
