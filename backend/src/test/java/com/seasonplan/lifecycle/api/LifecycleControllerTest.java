package com.seasonplan.lifecycle.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.lifecycle.domain.SeasonPlanStatus;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LifecycleControllerTest {
    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SeasonPlanTestFixture fixture = SeasonPlanTestFixture.create();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mvc = MockMvcBuilders.standaloneSetup(new LifecycleController(
                        fixture.seasonPlanService(),
                        fixture.pollenReservationService(),
                        fixture.magicTaskService(),
                        fixture.fairyAssignmentService(),
                        fixture.readinessChecklistService()
                ))
                .setControllerAdvice(new LifecycleExceptionHandler())
                .build();
    }

    @Test
    void createSubmitApproveAndReadinessEndpointsExposeLifecycle() throws Exception {
        String createResponse = mvc.perform(post("/season-plans")
                        .header("X-Actor-Role", "FAIRY_PLANNER")
                        .header("X-Actor-Id", "planner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SeasonPlanTestFixture.validPlanDraft("planner-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SeasonPlanStatus.DRAFT.name()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String planId = objectMapper.readTree(createResponse).get("id").asText();

        mvc.perform(post("/season-plans/{id}/submit", planId)
                        .header("X-Actor-Role", "FAIRY_PLANNER")
                        .header("X-Actor-Id", "planner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SeasonPlanStatus.SUBMITTED.name()));

        mvc.perform(post("/season-plans/{id}/approve", planId)
                        .header("X-Actor-Role", "CHIEF_GUARDIAN")
                        .header("X-Actor-Id", "guardian-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SeasonPlanStatus.APPROVED.name()));

        mvc.perform(get("/season-plans/{id}/readiness", planId)
                        .header("X-Actor-Role", "TALENT_COORDINATOR")
                        .header("X-Actor-Id", "coordinator-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.errors.pollenReservation[0]").value("confirmed pollen reservation is required"));
    }

    @Test
    void unauthorizedRoleReturnsForbiddenWithStructuredError() throws Exception {
        mvc.perform(post("/season-plans")
                        .header("X-Actor-Role", "CHIEF_GUARDIAN")
                        .header("X-Actor-Id", "guardian-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SeasonPlanTestFixture.validPlanDraft("guardian-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("role is not allowed for this command: CHIEF_GUARDIAN"));
    }
}
