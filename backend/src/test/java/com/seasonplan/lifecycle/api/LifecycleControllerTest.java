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
                        fixture.seasonPlanService(), fixture.pollenService(), fixture.magicTaskService(),
                        fixture.fairyAssignmentService(), fixture.readinessChecklistService(), () -> planner))
                .setControllerAdvice(new LifecycleExceptionHandler()).build();
    }

    @Test
    void reservationRequestsAcceptOptionalStableRequestIdWithoutChangingLegacyWireFormat() throws Exception {
        SeasonPlanTestFixture fixture = SeasonPlanTestFixture.create();
        var plan = fixture.seasonPlanService().createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
        CurrentActor guardian = new CurrentActor(UUID.randomUUID(), "guardian", Set.of(ActorRole.POLLEN_GUARDIAN));
        MockMvc guardianMvc = MockMvcBuilders.standaloneSetup(new LifecycleController(
                        fixture.seasonPlanService(), fixture.pollenService(), fixture.magicTaskService(),
                        fixture.fairyAssignmentService(), fixture.readinessChecklistService(), () -> guardian))
                .setControllerAdvice(new LifecycleExceptionHandler()).build();
        String payload = "{\"requestedAmount\":1500,\"requestId\":\"" + UUID.randomUUID() + "\"}";
        String first = guardianMvc.perform(post("/season-plans/{id}/pollen-reservations", plan.id())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedAmount").value(1500)).andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).get("id").asText();
        guardianMvc.perform(post("/season-plans/{id}/pollen-reservations", plan.id())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        guardianMvc.perform(post("/season-plans/{id}/pollen-reservations", plan.id())
                        .contentType(MediaType.APPLICATION_JSON).content(payload.replace("1500", "1501")))
                .andExpect(status().isBadRequest());
        guardianMvc.perform(post("/season-plans/{id}/pollen-reservations", plan.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"requestedAmount\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
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
