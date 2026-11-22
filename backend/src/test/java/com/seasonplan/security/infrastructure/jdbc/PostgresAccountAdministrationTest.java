package com.seasonplan.security.infrastructure.jdbc;

import com.seasonplan.identity.api.UserAdministrationException;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.identity.api.CurrentActor;
import com.seasonplan.identity.application.port.UserAccountRepository;
import com.seasonplan.identity.application.port.AccountTransaction;
import com.seasonplan.identity.api.LocalUserAdministration;
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
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@Testcontainers
class PostgresAccountAdministrationTest {
    /** Close workers and datasource before the Testcontainers afterAll callback stops PostgreSQL. */
    @org.junit.jupiter.api.AfterAll
    static void closeApplicationBeforeContainerStops(org.springframework.context.ConfigurableApplicationContext context) {
        assertThat(postgres.isRunning()).isTrue();
        // Evict through Spring so later test listeners do not access a closed cached context.
        new org.springframework.test.context.TestContextManager(PostgresAccountAdministrationTest.class).getTestContext()
                .markApplicationContextDirty(org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);
        assertThat(context.isActive()).isFalse();
    }

    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("seasonplan_test").withUsername("seasonplan").withPassword("seasonplan_test")
            .withEnv("DB_RUNTIME_USER", "seasonplan_app").withEnv("DB_RUNTIME_PASSWORD", "runtime-test-only")
            .withCopyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(
                    java.nio.file.Path.of("..", "infrastructure", "postgres", "010-runtime-role.sh").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/010-runtime-role.sh");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "seasonplan_app");
        registry.add("spring.datasource.password", () -> "runtime-test-only");
        registry.add("spring.liquibase.user", postgres::getUsername);
        registry.add("spring.liquibase.password", postgres::getPassword);
    }
    @Autowired LocalUserAdministration service;
    @Autowired UserAccountRepository accounts;
    @Autowired AccountTransaction transaction;
    @Autowired JdbcTemplate jdbc;

    @Test void applicationUsesRestrictedRuntimeAndSeparateLiquibaseOwner() {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("seasonplan_app");
        assertThat(jdbc.queryForObject("select pg_has_role(current_user, 'seasonplan', 'MEMBER')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select tableowner from pg_tables where schemaname = 'public' and tablename = 'audit_log_entries'", String.class)).isEqualTo("seasonplan");
    }

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
