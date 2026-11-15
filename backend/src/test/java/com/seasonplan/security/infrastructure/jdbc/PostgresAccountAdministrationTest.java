package com.seasonplan.security.infrastructure.jdbc;

import com.seasonplan.security.exception.UserAdministrationException;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.security.identity.CurrentActor;
import com.seasonplan.security.repository.UserAccountRepository;
import com.seasonplan.security.service.AccountTransaction;
import com.seasonplan.security.service.LocalUserAdministration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PostgresAccountAdministrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("seasonplan_test").withUsername("seasonplan").withPassword("seasonplan_test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired LocalUserAdministration service;
    @Autowired UserAccountRepository accounts;
    @Autowired AccountTransaction transaction;
    @Autowired JdbcTemplate jdbc;

    @Test void duplicateAndStalePasswordsLeaveExistingCredentialsUnchanged() {
        var admin = new CurrentActor(UUID.randomUUID(), "admin", Set.of(), true);
        String suffix = UUID.randomUUID().toString();
        var first = service.createUser(admin, new LocalUserAdministration.CreateUserCommand("u" + suffix, "First", "first-password", Set.of(ActorRole.FAIRY_PLANNER)));
        var second = service.createUser(admin, new LocalUserAdministration.CreateUserCommand("v" + suffix, "Second", "other-password", Set.of(ActorRole.TALENT_COORDINATOR)));
        var actor = new CurrentActor(first.id(), first.username(), first.roles());
        String hash = jdbc.queryForObject("select password_hash from local_credentials where user_id = ?", String.class, first.id());
        assertThatThrownBy(() -> service.updateOwnCredentials(actor, new LocalUserAdministration.CredentialChangeCommand("first-password", second.username(), "replacement-password")))
                .isInstanceOf(UserAdministrationException.class)
                .satisfies(error -> assertThat(((UserAdministrationException) error).kind()).isEqualTo(UserAdministrationException.Kind.DUPLICATE_USERNAME));
        assertThat(jdbc.queryForObject("select password_hash from local_credentials where user_id = ?", String.class, first.id())).isEqualTo(hash);
        assertThat(jdbc.queryForObject("select username from users where id = ?", String.class, first.id())).isEqualTo(first.username());
        service.updateOwnCredentials(actor, new LocalUserAdministration.CredentialChangeCommand("first-password", first.username(), "replacement-password"));
        assertThatThrownBy(() -> service.updateOwnCredentials(actor, new LocalUserAdministration.CredentialChangeCommand("first-password", first.username(), "another-password")))
                .isInstanceOf(UserAdministrationException.class)
                .satisfies(error -> assertThat(((UserAdministrationException) error).kind()).isEqualTo(UserAdministrationException.Kind.INVALID_CURRENT_PASSWORD));
    }

    @Test void laterFailureRollsBackUserCredentialAndRolesTogether() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> transaction.execute(() -> {
            accounts.insert(id, "u" + id, "User", false, Set.of(ActorRole.FAIRY_PLANNER), "{bcrypt}encoded");
            throw new IllegalStateException("simulated failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from local_credentials where user_id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, id)).isZero();
    }
}
