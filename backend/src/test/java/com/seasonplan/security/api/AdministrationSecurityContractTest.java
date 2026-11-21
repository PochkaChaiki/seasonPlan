package com.seasonplan.security.api;

import com.seasonplan.security.config.SecurityConfiguration;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.security.identity.CurrentActor;
import com.seasonplan.security.identity.CurrentActorProvider;
import com.seasonplan.security.api.AuthController;
import com.seasonplan.security.api.AdminUserController;
import com.seasonplan.security.domain.ManagedUser;
import com.seasonplan.security.infrastructure.spring.LocalUserDetails;
import com.seasonplan.security.service.LocalUserAdministration;
import com.seasonplan.security.exception.UserAdministrationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, AdminUserController.class})
@Import(SecurityConfiguration.class)
class AdministrationSecurityContractTest {
    private static final LocalUserDetails ADMIN = new LocalUserDetails(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "admin", "Administrator", "{noop}password", true, true, Set.of());

    private static final CurrentActor ADMIN_ACTOR = new CurrentActor(ADMIN.userId(), ADMIN.username(), Set.of(), true);

    @org.junit.jupiter.api.BeforeEach
    void actor() { willReturn(ADMIN_ACTOR).given(currentActorProvider).currentActor(); }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private CurrentActorProvider currentActorProvider;

    @MockBean
    private LocalUserAdministration users;

    @Test
    void adminCanCreateAUserButOrdinaryAndAnonymousCallersCannot() throws Exception {
        willReturn(new ManagedUser(UUID.fromString("00000000-0000-0000-0000-000000000002"), "planner", "Planner",
                true, Set.of(ActorRole.FAIRY_PLANNER))).given(users).createUser(any(), any());

        String body = """
                {"username":" Planner ","displayName":"Planner","password":"long-safe-password","roles":["FAIRY_PLANNER"]}
                """;

        mvc.perform(post("/admin/users").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/admin/users").with(user("ordinary").authorities(new SimpleGrantedAuthority("ROLE_FAIRY_PLANNER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/users").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/admin/users/00000000-0000-0000-0000-000000000002"))
                .andExpect(jsonPath("$.username").value("planner"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(users).createUser(org.mockito.ArgumentMatchers.eq(ADMIN_ACTOR), any());
    }

    @Test
    void rejectsInvalidUserPayloadBeforeItCanReachTheService() throws Exception {
        mvc.perform(post("/admin/users").with(user(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bad space\",\"displayName\":\" \",\"password\":\"short\",\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        org.mockito.Mockito.verifyNoInteractions(users);
    }

    @Test
    void authenticatedUserCanChangeOnlyOwnCredentialsAndTheirSessionIsInvalidated() throws Exception {
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();

        mvc.perform(put("/auth/me/credentials").with(user(ADMIN)).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"current-password\",\"username\":\"new-admin\",\"newPassword\":\"new-long-password\",\"userId\":\"00000000-0000-0000-0000-000000000099\"}"))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        verify(users).updateOwnCredentials(org.mockito.ArgumentMatchers.eq(ADMIN_ACTOR), any());
    }

    @Test
    void wrongCurrentPasswordReturnsASafeErrorAndKeepsTheSession() throws Exception {
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        willThrow(new UserAdministrationException(UserAdministrationException.Kind.INVALID_CURRENT_PASSWORD, "Current password is incorrect"))
                .given(users).updateOwnCredentials(org.mockito.ArgumentMatchers.eq(ADMIN_ACTOR), any());

        mvc.perform(put("/auth/me/credentials").with(user(ADMIN)).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"username\":\"admin\",\"newPassword\":\"new-long-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(session.isInvalid()).isFalse();
    }
}
