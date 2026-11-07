package com.seasonplan.security;

import com.seasonplan.lifecycle.config.SecurityConfiguration;
import com.seasonplan.lifecycle.application.port.CurrentActorProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfiguration.class)
class AuthenticationSecurityContractTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private CurrentActorProvider currentActorProvider;

    @MockBean
    private LocalUserAdministration userAdministrationService;

    @Test
    void businessRoutesAreJson401AndActorHeadersCannotAuthenticate() throws Exception {
        mvc.perform(get("/season-plans")
                        .header("X-Actor-Id", "00000000-0000-0000-0000-000000000001")
                        .header("X-Actor-Role", "FAIRY_PLANNER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void csrfLoginAndMeUseAServerSession() throws Exception {
        given(userDetailsService.loadUserByUsername("planner"))
                .willReturn(new LocalUserDetails(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "planner", "Planner", "{noop}secret", true,
                        java.util.Set.of(com.seasonplan.lifecycle.domain.ActorRole.FAIRY_PLANNER)));

        mvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));

        mvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "planner")
                        .param("password", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("planner"));
    }

    @Test
    void loginResponseDoesNotExposeCredentialsAndIncludesAdministratorFlag() throws Exception {
        given(userDetailsService.loadUserByUsername("admin"))
                .willReturn(new LocalUserDetails(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "admin", "Administrator", "{noop}secret", true, true, java.util.Set.of()));

        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin").param("password", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Administrator"))
                .andExpect(jsonPath("$.administrator").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
