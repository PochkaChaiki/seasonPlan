package com.seasonplan.security;

import com.seasonplan.lifecycle.domain.CurrentActor;
import com.seasonplan.lifecycle.service.CurrentActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final CurrentActorProvider actorProvider;
    private final LocalUserAdministration userAdministrationService;

    public AuthController(CurrentActorProvider actorProvider, LocalUserAdministration userAdministrationService) {
        this.actorProvider = actorProvider;
        this.userAdministrationService = userAdministrationService;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(@RequestAttribute("_csrf") CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal LocalUserDetails user) { return UserResponse.from(actorProvider.currentActor(), user.displayName(), user.systemAdministrator()); }

    @org.springframework.web.bind.annotation.PutMapping("/me/credentials")
    public ResponseEntity<Void> updateOwnCredentials(@AuthenticationPrincipal LocalUserDetails user,
                                                      @org.springframework.web.bind.annotation.RequestBody CredentialChangeRequest request,
                                                      HttpServletRequest httpRequest) {
        userAdministrationService.updateOwnCredentials(user, new LocalUserAdministrationService.CredentialChangeCommand(
                request.currentPassword(), request.username(), request.newPassword()));
        var session = httpRequest.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    record UserResponse(java.util.UUID id, String username, String displayName, java.util.Set<?> roles, boolean administrator) {
        static UserResponse from(CurrentActor actor, String displayName, boolean administrator) {
            return new UserResponse(actor.userId(), actor.username(), displayName, actor.roles(), administrator);
        }
    }

    record CredentialChangeRequest(String currentPassword, String username, String newPassword) { }
}
