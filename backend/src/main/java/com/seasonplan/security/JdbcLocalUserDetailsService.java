package com.seasonplan.security;

import com.seasonplan.lifecycle.domain.ActorRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.UUID;

@Service
public class JdbcLocalUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    public JdbcLocalUserDetailsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalized;
        try {
            normalized = UsernameNormalizer.normalize(username);
        } catch (IllegalArgumentException error) {
            throw new UsernameNotFoundException("invalid credentials");
        }
        return jdbc.query("""
                select u.id, u.username, u.display_name, u.enabled, u.system_admin, c.password_hash
                  from users u join local_credentials c on c.user_id = u.id
                 where u.username = ?
                """, rs -> {
            if (!rs.next()) throw new UsernameNotFoundException("invalid credentials");
            UUID id = rs.getObject("id", UUID.class);
            LinkedHashSet<ActorRole> roles = new LinkedHashSet<>(jdbc.query(
                    "select role from user_roles where user_id = ?", (rolesRs, row) -> ActorRole.valueOf(rolesRs.getString(1)), id));
            return new LocalUserDetails(id, rs.getString("username"), rs.getString("display_name"),
                    rs.getString("password_hash"), rs.getBoolean("enabled"), rs.getBoolean("system_admin"), roles);
        }, normalized);
    }
}
