package com.seasonplan.security;

import com.seasonplan.lifecycle.domain.ActorRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public record LocalUserDetails(UUID userId, String username, String displayName, String password,
                               boolean enabled, boolean systemAdministrator, Set<ActorRole> roles) implements UserDetails {
    public LocalUserDetails(UUID userId, String username, String displayName, String password,
                            boolean enabled, Set<ActorRole> roles) {
        this(userId, username, displayName, password, enabled, false, roles);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        java.util.List<GrantedAuthority> authorities = new java.util.ArrayList<>(roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList());
        if (systemAdministrator) authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
        return authorities;
    }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
