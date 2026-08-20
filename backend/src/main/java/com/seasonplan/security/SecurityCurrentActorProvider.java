package com.seasonplan.security;

import com.seasonplan.lifecycle.domain.ActorRole;
import com.seasonplan.lifecycle.domain.CurrentActor;
import com.seasonplan.lifecycle.service.CurrentActorProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SecurityCurrentActorProvider implements CurrentActorProvider {
    @Override
    public CurrentActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof LocalUserDetails user)) {
            throw new org.springframework.security.access.AccessDeniedException("authenticated user is required");
        }
        return new CurrentActor(user.userId(), user.username(), Set.copyOf(user.roles()));
    }
}
