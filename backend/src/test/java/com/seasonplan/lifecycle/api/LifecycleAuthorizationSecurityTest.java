package com.seasonplan.lifecycle.api;

import com.seasonplan.workflows.adapter.in.web.LifecycleController;

import com.seasonplan.shared.application.NotFoundException;
import com.seasonplan.work.api.WorkPreparation;
import com.seasonplan.pollen.api.PollenOperations;
import com.seasonplan.season.api.SeasonPlanning;
import com.seasonplan.bootstrap.SecurityConfiguration;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.identity.adapter.out.spring.LocalUserDetails;
import com.seasonplan.identity.adapter.out.spring.SecurityCurrentActorProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Set;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LifecycleController.class)
@Import({SecurityConfiguration.class, SecurityCurrentActorProvider.class})
class LifecycleAuthorizationSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean UserDetailsService userDetailsService;
    @MockBean SeasonPlanning plans;
    @MockBean PollenOperations pollen;
    @MockBean WorkPreparation tasks;
    private static final UUID ID = UUID.randomUUID();

    private LocalUserDetails principal(boolean admin, ActorRole... roles) {
        return new LocalUserDetails(UUID.randomUUID(), "test", "Test", "hash", true, admin, Set.of(roles));
    }

    @Test void reconcileRequiresGuardianAndUsesTrustedActorWithStableUnknownReference() throws Exception {
        String route = "/pollen-operations/{id}/reconcile";
        mvc.perform(post(route, ID).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(route, ID).with(user(principal(false, ActorRole.FAIRY_PLANNER))).with(csrf())).andExpect(status().isForbidden());
        var guardian = principal(false, ActorRole.POLLEN_GUARDIAN);
        mvc.perform(post(route, ID).with(user(guardian))).andExpect(status().isForbidden());
        when(pollen.reconcileOperation(eq(ID), eq(ActorRole.POLLEN_GUARDIAN), eq(guardian.userId().toString())))
                .thenThrow(new com.seasonplan.pollen.api.PollenCommandPendingException(ID));
        mvc.perform(post(route, ID).with(user(guardian)).with(csrf()).header("X-Actor-Id", "forged"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.operationId").value(ID.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("POLLEN_OPERATION_PENDING"));
        verify(pollen).reconcileOperation(ID, ActorRole.POLLEN_GUARDIAN, guardian.userId().toString());
        verifyNoMoreInteractions(pollen);
    }

    @Test void deniedCommandsCannotBeElevatedByHeadersOrBody() throws Exception {
        var admin = principal(true);
        mvc.perform(patch("/tasks/{id}", ID).with(user(admin)).with(csrf()).header("X-Actor-Role", "TALENT_COORDINATOR")
                .contentType(MediaType.APPLICATION_JSON).content("{\"actorRole\":\"TALENT_COORDINATOR\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/season-plans/{id}", ID).with(user(admin)).with(csrf()).header("X-Actor-Role", "FAIRY_PLANNER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"actorRole\":\"FAIRY_PLANNER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/season-plans/{id}/pollen-reservations", ID).with(user(admin)).with(csrf())
                .header("X-Actor-Role", "POLLEN_GUARDIAN").contentType(MediaType.APPLICATION_JSON).content("{\"requestedAmount\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/season-plans/{id}/mark-ritual-ready", ID).with(user(principal(false, ActorRole.POLLEN_GUARDIAN))).with(csrf()))
                .andExpect(status().isForbidden());
        for (String action : Set.of("approve", "return")) {
            mvc.perform(post("/season-plans/{id}/" + action, ID).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"comment\":\"test\"}" )).andExpect(status().isForbidden());
        }
        verifyNoInteractions(plans, pollen, tasks);
    }

    @Test void unauthenticatedAndCsrfBoundariesRemainEnforced() throws Exception {
        mvc.perform(post("/season-plans/{id}/mark-ritual-ready", ID).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/season-plans/{id}/mark-ritual-ready", ID).with(user(principal(false, ActorRole.TALENT_COORDINATOR))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(plans, pollen, tasks);
    }

    @Test void multiRolePrincipalUsesAllowedRoleAndTrustedIdAtReviewBoundary() throws Exception {
        var actor = principal(false, ActorRole.FAIRY_PLANNER, ActorRole.CHIEF_GUARDIAN);
        when(plans.approve(any(), any(), any(), any(), any())).thenThrow(new NotFoundException("missing"));
        when(plans.returnForRevision(any(), any(), any(), any())).thenThrow(new NotFoundException("missing"));
        for (String action : Set.of("approve", "return")) {
            mvc.perform(post("/season-plans/{id}/" + action, ID).with(user(actor)).with(csrf())
                    .header("X-Actor-Id", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"comment\":\"ok\",\"actorRole\":\"FAIRY_PLANNER\"}"))
                    .andExpect(status().isNotFound());
        }
        verify(plans).approve(eq(ID), eq("ok"), any(), eq(ActorRole.CHIEF_GUARDIAN), eq(actor.userId().toString()));
        verify(plans).returnForRevision(ID, "ok", ActorRole.CHIEF_GUARDIAN, actor.userId().toString());
    }
}
