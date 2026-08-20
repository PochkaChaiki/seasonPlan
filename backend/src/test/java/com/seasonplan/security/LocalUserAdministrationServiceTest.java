package com.seasonplan.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocalUserAdministrationServiceTest {
    @Test
    void existingSystemAdministratorMakesBootstrapInitialOnly() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(1);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        LocalUserAdministrationService service = new LocalUserAdministrationService(jdbc, encoder);

        service.createInitialSystemAdministrator("dev-admin", "Developer", "long-safe-password");

        assertThat(jdbc.writes).isEmpty();
        verifyNoInteractions(encoder);
    }

    @Test
    void bootstrapCreatesAnAdministratorWithEncodedCredentialAndNoBusinessRole() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(0);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("long-safe-password")).thenReturn("{bcrypt}encoded-value");
        LocalUserAdministrationService service = new LocalUserAdministrationService(jdbc, encoder);

        service.createInitialSystemAdministrator(" Dev-Admin ", "Developer", "long-safe-password");

        assertThat(jdbc.writes).hasSize(2);
        assertThat(jdbc.writes.get(0).sql()).contains("system_admin").contains("true");
        assertThat(jdbc.writes.get(0).arguments()).contains("dev-admin", "Developer");
        assertThat(jdbc.writes.get(1).sql()).contains("local_credentials");
        assertThat(jdbc.writes.get(1).arguments()).contains("{bcrypt}encoded-value").doesNotContain("long-safe-password");
    }

    @Test
    void credentialChangeRejectsWrongCurrentPasswordWithoutDatabaseWrites() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(0);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("wrong-password", "{bcrypt}old")).thenReturn(false);
        LocalUserAdministrationService service = new LocalUserAdministrationService(jdbc, encoder);
        LocalUserDetails user = new LocalUserDetails(java.util.UUID.randomUUID(), "old", "Old", "{bcrypt}old", true, java.util.Set.of());

        assertThatThrownBy(() -> service.updateOwnCredentials(user,
                new LocalUserAdministrationService.CredentialChangeCommand("wrong-password", "new-user", "new-long-password")))
                .isInstanceOf(UserAdministrationException.class)
                .hasMessageContaining("Current password");

        assertThat(jdbc.writes).isEmpty();
        verify(encoder).matches("wrong-password", "{bcrypt}old");
    }

    @Test
    void credentialChangeWritesNormalizedUsernameAndEncodedHash() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(0);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("current-password", "{bcrypt}old")).thenReturn(true);
        when(encoder.encode("new-long-password")).thenReturn("{bcrypt}new");
        LocalUserAdministrationService service = new LocalUserAdministrationService(jdbc, encoder);
        LocalUserDetails user = new LocalUserDetails(java.util.UUID.randomUUID(), "old", "Old", "{bcrypt}old", true, java.util.Set.of());

        service.updateOwnCredentials(user,
                new LocalUserAdministrationService.CredentialChangeCommand("current-password", " New-User ", "new-long-password"));

        assertThat(jdbc.writes).hasSize(2);
        assertThat(jdbc.writes.get(0).arguments()).contains("new-user", user.userId());
        assertThat(jdbc.writes.get(1).arguments()).contains("{bcrypt}new", user.userId()).doesNotContain("new-long-password");
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final int systemAdministratorCount;
        private final List<Write> writes = new ArrayList<>();

        private RecordingJdbcTemplate(int systemAdministratorCount) { this.systemAdministratorCount = systemAdministratorCount; }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            return (T) Integer.valueOf(systemAdministratorCount);
        }

        @Override
        public int update(String sql, Object... args) {
            writes.add(new Write(sql, List.of(args)));
            return 1;
        }
    }

    private record Write(String sql, List<Object> arguments) { }
}
