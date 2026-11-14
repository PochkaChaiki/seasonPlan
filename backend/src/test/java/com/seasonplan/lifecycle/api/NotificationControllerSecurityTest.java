package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.application.port.CurrentActorProvider;
import com.seasonplan.lifecycle.application.port.NotificationInboxRepository;
import com.seasonplan.lifecycle.config.SecurityConfiguration;
import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.CurrentActor;
import com.seasonplan.lifecycle.domain.NotificationInboxItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NotificationController.class)
@Import(SecurityConfiguration.class)
class NotificationControllerSecurityTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private CurrentActorProvider actors;
    @MockBean private NotificationInboxRepository inbox;

    @Test void unauthenticatedCallerCannotReadNotificationsOrSpoofActor() throws Exception {
        mvc.perform(get("/notifications").header("X-Actor-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(actors, inbox);
    }

    @Test @WithMockUser
    void ignoresRecipientParametersAndUsesServerActorIdentity() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        when(actors.currentActor()).thenReturn(new CurrentActor(owner, "planner", Set.of(ActorRole.FAIRY_PLANNER)));
        when(inbox.findLatestByRecipient(owner)).thenReturn(List.of(new NotificationInboxItem("event-1", plan,
                "SEASON_PLAN_RETURNED", "revise", Instant.parse("2026-01-01T00:00:00Z"))));
        mvc.perform(get("/notifications").param("recipient", other.toString()).header("X-Actor-Id", other.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].eventId").value("event-1"))
                .andExpect(jsonPath("$[0].seasonPlanId").value(plan.toString()))
                .andExpect(jsonPath("$[0].eventType").value("SEASON_PLAN_RETURNED"))
                .andExpect(jsonPath("$[0].message").value("revise"))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$[0].recipient").doesNotExist());
        verify(inbox).findLatestByRecipient(owner);
        verifyNoMoreInteractions(inbox);
    }
}
