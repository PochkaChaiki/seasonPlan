package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.CurrentActor;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void conditionalApprovalIsReturnedAndReadinessCannotBeBypassedByManualChecklist() throws Exception {
        SeasonPlanTestFixture fixture = SeasonPlanTestFixture.create();
        var plan = fixture.seasonPlanService().createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
        fixture.seasonPlanService().submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner");
        var reservation = fixture.pollenService().requestReservation(plan.id(), 100, ActorRole.POLLEN_GUARDIAN, "pollen");
        fixture.pollenService().confirmReservation(reservation.id(), 100, "ok", ActorRole.POLLEN_GUARDIAN, "pollen");
        fixture.seasonPlanService().verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "chief");
        CurrentActor chief = new CurrentActor(UUID.randomUUID(), "chief", Set.of(ActorRole.CHIEF_GUARDIAN));
        MockMvc chiefMvc = MockMvcBuilders.standaloneSetup(new LifecycleController(
                        fixture.seasonPlanService(), fixture.pollenService(), fixture.magicTaskService(),
                        fixture.fairyAssignmentService(), fixture.readinessChecklistService(), () -> chief))
                .setControllerAdvice(new LifecycleExceptionHandler()).build();
        chiefMvc.perform(post("/season-plans/{id}/approve", plan.id()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"conditional", "conditions":[{"key":"weather-evidence", "displayText":"Weather evidence",
                                 "evidenceRequirement":"Calibration record", "ownerRole":"TALENT_COORDINATOR"}]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvalConditions[0].key").value("weather-evidence"))
                .andExpect(jsonPath("$.approvalConditions[0].ownerRole").value("TALENT_COORDINATOR"));
        fixture.readinessChecklistService().completeItem(plan.id(), "weather-evidence", ActorRole.TALENT_COORDINATOR, "coordinator");
        chiefMvc.perform(get("/season-plans/{id}/readiness", plan.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.errors['weather-evidence']").isArray());
        chiefMvc.perform(get("/season-plans/{id}/review-history", plan.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.action == 'SEASON_PLAN_APPROVED')]").isNotEmpty());
    }

    @Test
    void historyIsVisibleOnlyToChiefOrPlanCreatorPlanner() throws Exception {
        SeasonPlanTestFixture fixture = SeasonPlanTestFixture.create();
        UUID owner = UUID.randomUUID();
        var plan = fixture.seasonPlanService().createDraft(SeasonPlanTestFixture.validPlanDraft(owner.toString()), ActorRole.FAIRY_PLANNER, owner.toString());
        for (var actor : List.of(new CurrentActor(owner, "owner", Set.of(ActorRole.FAIRY_PLANNER)),
                new CurrentActor(UUID.randomUUID(), "chief", Set.of(ActorRole.CHIEF_GUARDIAN)))) {
            historyMvc(fixture, actor).perform(get("/season-plans/{id}/review-history", plan.id()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("SEASON_PLAN_CREATED"));
        }
        for (var actor : List.of(new CurrentActor(UUID.randomUUID(), "other", Set.of(ActorRole.FAIRY_PLANNER)),
                new CurrentActor(owner, "coordinator", Set.of(ActorRole.TALENT_COORDINATOR)))) {
            historyMvc(fixture, actor).perform(get("/season-plans/{id}/review-history", plan.id())).andExpect(status().isForbidden());
        }
    }

    private MockMvc historyMvc(SeasonPlanTestFixture fixture, CurrentActor actor) {
        return MockMvcBuilders.standaloneSetup(new LifecycleController(
                fixture.seasonPlanService(), fixture.pollenService(), fixture.magicTaskService(),
                fixture.fairyAssignmentService(), fixture.readinessChecklistService(), () -> actor))
                .setControllerAdvice(new LifecycleExceptionHandler()).build();
    }

    @Test
    void reviewDecisionEndpointsRequireChiefGuardianAtSecurityBoundary() throws Exception {
        for (String method : List.of("approve", "returnForRevision")) {
            var handler = java.util.Arrays.stream(LifecycleController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(method)).findFirst().orElseThrow();
            var auth = handler.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            org.assertj.core.api.Assertions.assertThat(auth).isNotNull();
            org.assertj.core.api.Assertions.assertThat(auth.value()).contains("CHIEF_GUARDIAN");
        }
    }

    @Test
    void verifiedPlanCannotBePatchedByPlanner() throws Exception {
        SeasonPlanTestFixture fixture = SeasonPlanTestFixture.create();
        UUID plannerId = UUID.randomUUID();
        var plan = fixture.seasonPlanService().createDraft(SeasonPlanTestFixture.validPlanDraft(plannerId.toString()), ActorRole.FAIRY_PLANNER, plannerId.toString());
        fixture.seasonPlanService().submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, plannerId.toString());
        fixture.seasonPlanService().verify(plan.id(), ActorRole.CHIEF_GUARDIAN, "chief");
        var actor = new CurrentActor(plannerId, "planner", Set.of(ActorRole.FAIRY_PLANNER));
        MockMvc plannerMvc = historyMvc(fixture, actor);
        plannerMvc.perform(patch("/season-plans/{id}", plan.id()).contentType(MediaType.APPLICATION_JSON).content("""
                {"seasonType":"SUMMER","targetTerritories":["north-grove"],
                 "timeWindows":[{"start":"2026-03-01T09:00:00","end":"2026-03-01T12:00:00"}],
                 "naturalPhenomenonParameters":{"bloom":"early"},"targetClimateMetrics":{"temperature":17.5}}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").isArray());
        org.assertj.core.api.Assertions.assertThat(plan.seasonType()).isEqualTo("SPRING");
    }

    @Test
    void guardianVerifiesSubmittedPlanThroughRest() throws Exception {
        SeasonPlanTestFixture fixture = SeasonPlanTestFixture.create();
        var plan = fixture.seasonPlanService().createDraft(SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
        fixture.seasonPlanService().submitForApproval(plan.id(), ActorRole.FAIRY_PLANNER, "planner");
        CurrentActor guardian = new CurrentActor(UUID.randomUUID(), "guardian", Set.of(ActorRole.CHIEF_GUARDIAN));
        MockMvc guardianMvc = MockMvcBuilders.standaloneSetup(new LifecycleController(
                        fixture.seasonPlanService(), fixture.pollenService(), fixture.magicTaskService(),
                        fixture.fairyAssignmentService(), fixture.readinessChecklistService(), () -> guardian))
                .setControllerAdvice(new LifecycleExceptionHandler()).build();
        guardianMvc.perform(post("/season-plans/{id}/verify", plan.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED"));
        guardianMvc.perform(post("/season-plans/{id}/verify", plan.id())).andExpect(status().isBadRequest());
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
