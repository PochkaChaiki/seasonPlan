package com.seasonplan.security.infrastructure.jdbc;

import com.seasonplan.security.exception.UserAdministrationException;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.security.repository.UserAccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class JdbcUserAccountRepository implements UserAccountRepository {
    private final JdbcTemplate jdbc;

    public JdbcUserAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean hasSystemAdministrator() {
        Integer count = jdbc.queryForObject("select count(*) from users where system_admin", Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void insert(UUID id, String username, String displayName, boolean systemAdministrator, Set<ActorRole> roles, String encodedHash) {
        try {
            jdbc.update("insert into users (id, username, display_name, enabled, system_admin) values (?, ?, ?, true, ?)",
                    id, username, displayName, systemAdministrator);
        } catch (DuplicateKeyException error) {
            if (systemAdministrator) throw error;
            throw duplicateUsername();
        }
        jdbc.update("insert into local_credentials (user_id, password_hash) values (?, ?)", id, encodedHash);
        for (ActorRole role : roles) jdbc.update("insert into user_roles (user_id, role) values (?, ?)", id, role.name());
    }

    @Override
    public Optional<String> lockCredentialHash(UUID userId) {
        return jdbc.query("select password_hash from local_credentials where user_id = ? for update",
                (rs, row) -> rs.getString("password_hash"), userId).stream().findFirst();
    }

    @Override
    public void updateCredentials(UUID userId, String username, String encodedHash) {
        try {
            jdbc.update("update users set username = ? where id = ?", username, userId);
        } catch (DuplicateKeyException error) {
            throw duplicateUsername();
        }
        jdbc.update("update local_credentials set password_hash = ?, updated_at = now() where user_id = ?", encodedHash, userId);
    }

    private UserAdministrationException duplicateUsername() {
        return new UserAdministrationException(UserAdministrationException.Kind.DUPLICATE_USERNAME, "Username is already in use");
    }
}
