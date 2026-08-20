package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.CurrentActor;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LifecycleControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SeasonPlanTestFixture fixture = SeasonPlanTestFixture.create();
        CurrentActor planner = new CurrentActor(UUID.fromString("00000000-0000-0000-0000-000000000001"), "planner", Set.of(ActorRole.FAIRY_PLANNER));
        mvc = MockMvcBuilders.standaloneSetup(new LifecycleController(
                        fixture.seasonPlanService(), fixture.pollenReservationService(), fixture.magicTaskService(),
                        fixture.fairyAssignmentService(), fixture.readinessChecklistService(), () -> planner))
                .setControllerAdvice(new LifecycleExceptionHandler()).build();
    }

    @Test
    void createPlanDerivesOwnerFromCurrentActorInsteadOfRequestFields() throws Exception {
        mvc.perform(post("/season-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"createdBy":"attacker","seasonType":"SPRING","targetTerritories":["north-grove"],
                             "timeWindows":[{"start":"2026-03-01T09:00:00","end":"2026-03-01T12:00:00"}],
                             "naturalPhenomenonParameters":{"bloom":"early"},"targetClimateMetrics":{"temperature":17.5}}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value("00000000-0000-0000-0000-000000000001"));
    }
}
