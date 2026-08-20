package com.seasonplan.security;

import com.seasonplan.lifecycle.domain.ActorRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
public class LocalUserAdministrationService implements LocalUserAdministration {
    private static final int MAX_DISPLAY_NAME_LENGTH = 200;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public LocalUserAdministrationService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ManagedUser createUser(CreateUserCommand command) {
        CreateUserCommand validated = validateCreateUser(command.username(), command.displayName(), command.password(), command.roles());
        String username = validated.username();
        String displayName = validated.displayName();
        Set<ActorRole> roles = validated.roles();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into users (id, username, display_name, enabled, system_admin) values (?, ?, ?, true, false)",
                    id, username, displayName);
            jdbc.update("insert into local_credentials (user_id, password_hash) values (?, ?)", id, passwordEncoder.encode(command.password()));
            for (ActorRole role : roles) {
                jdbc.update("insert into user_roles (user_id, role) values (?, ?)", id, role.name());
            }
        } catch (DataIntegrityViolationException error) {
            throw new UserAdministrationException(UserAdministrationException.Kind.DUPLICATE_USERNAME, "Username is already in use");
        }
        return new ManagedUser(id, username, displayName, true, roles);
    }

    @Transactional
    public void updateOwnCredentials(LocalUserDetails user, CredentialChangeCommand command) {
        String username = validatedUsername(command.username());
        validatedPassword(command.newPassword());
        if (isBlank(command.currentPassword()) || !passwordEncoder.matches(command.currentPassword(), user.password())) {
            throw new UserAdministrationException(UserAdministrationException.Kind.INVALID_CURRENT_PASSWORD, "Current password is incorrect");
        }
        try {
            jdbc.update("update users set username = ? where id = ?", username, user.userId());
            jdbc.update("update local_credentials set password_hash = ?, updated_at = now() where user_id = ?",
                    passwordEncoder.encode(command.newPassword()), user.userId());
        } catch (DataIntegrityViolationException error) {
            throw new UserAdministrationException(UserAdministrationException.Kind.DUPLICATE_USERNAME, "Username is already in use");
        }
    }

    @Transactional
    public void createInitialSystemAdministrator(String username, String displayName, String password) {
        if (jdbc.queryForObject("select count(*) from users where system_admin", Integer.class) > 0) return;
        String normalizedUsername = validatedUsername(username);
        String validDisplayName = validatedDisplayName(displayName);
        validatedPassword(password);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, username, display_name, enabled, system_admin) values (?, ?, ?, true, true)",
                id, normalizedUsername, validDisplayName);
        jdbc.update("insert into local_credentials (user_id, password_hash) values (?, ?)", id, passwordEncoder.encode(password));
    }

    static String validatedUsername(String username) {
        try {
            return UsernameNormalizer.normalize(username);
        } catch (IllegalArgumentException error) {
            throw new UserAdministrationException(UserAdministrationException.Kind.VALIDATION, "Username must contain 1 to 100 lowercase letters, digits, dots, underscores, or hyphens");
        }
    }

    static CreateUserCommand validateCreateUser(String username, String displayName, String password, Set<ActorRole> roles) {
        return new CreateUserCommand(validatedUsername(username), validatedDisplayName(displayName), validatedPassword(password), validatedRoles(roles));
    }

    static String validatedDisplayName(String displayName) {
        if (isBlank(displayName) || displayName.trim().length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new UserAdministrationException(UserAdministrationException.Kind.VALIDATION, "Display name is required and must be at most 200 characters");
        }
        return displayName.trim();
    }

    static String validatedPassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new UserAdministrationException(UserAdministrationException.Kind.VALIDATION, "Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
        return password;
    }

    static Set<ActorRole> validatedRoles(Set<ActorRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new UserAdministrationException(UserAdministrationException.Kind.VALIDATION, "At least one business role is required");
        }
        return Set.copyOf(roles);
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    public record CreateUserCommand(String username, String displayName, String password, Set<ActorRole> roles) { }
    public record CredentialChangeCommand(String currentPassword, String username, String newPassword) { }
}
