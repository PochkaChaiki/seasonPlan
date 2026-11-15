package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.exception.NotFoundException;
import com.seasonplan.lifecycle.service.MagicTaskService;
import com.seasonplan.lifecycle.service.PollenService;
import com.seasonplan.lifecycle.service.SeasonPlanService;
import com.seasonplan.security.config.SecurityConfiguration;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.security.infrastructure.spring.LocalUserDetails;
import com.seasonplan.security.infrastructure.spring.SecurityCurrentActorProvider;
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
    @MockBean SeasonPlanService plans;
    @MockBean PollenService pollen;
    @MockBean MagicTaskService tasks;
    private static final UUID ID = UUID.randomUUID();

    private LocalUserDetails principal(boolean admin, ActorRole... roles) {
        return new LocalUserDetails(UUID.randomUUID(), "test", "Test", "hash", true, admin, Set.of(roles));
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
