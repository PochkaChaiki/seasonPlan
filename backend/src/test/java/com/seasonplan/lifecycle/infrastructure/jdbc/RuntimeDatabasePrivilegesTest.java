package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seasonplan.audit.adapter.out.persistence.JdbcAuditLogRepository;
import com.seasonplan.audit.api.*;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.lifecycle.service.SeasonPlanTestFixture;
import com.seasonplan.season.adapter.out.persistence.JdbcSeasonPlanRepository;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class RuntimeDatabasePrivilegesTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("seasonplan_privileges").withUsername("seasonplan_owner").withPassword("owner-test-only")
            .withEnv("DB_RUNTIME_USER", "seasonplan_app").withEnv("DB_RUNTIME_PASSWORD", "bootstrap-test-only");
    private static JdbcTemplate runtime;

    @BeforeAll static void migrateAsOwnerAndConnectAsRuntime() throws Exception {
        var ownerDataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var owner = new JdbcTemplate(ownerDataSource);
        // This fixture represents DBA provisioning, not permissions supplied by the test.
        owner.execute("create role seasonplan_runtime nologin nosuperuser nocreatedb nocreaterole noreplication nobypassrls");
        owner.execute("create role privilege_test_app login nosuperuser nocreatedb nocreaterole noreplication nobypassrls password 'runtime-test-only' in role seasonplan_runtime");
        var migration = new SpringLiquibase();
        migration.setDataSource(ownerDataSource);
        migration.setResourceLoader(new org.springframework.core.io.DefaultResourceLoader());
        migration.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
        migration.afterPropertiesSet();
        runtime = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "privilege_test_app", "runtime-test-only"));
    }

    @Test void restrictedIdentityCanPersistBusinessDataAndAppendAndReadAudit() {
        assertThat(runtime.queryForObject("select current_user", String.class)).isEqualTo("privilege_test_app");
        var draft = SeasonPlanTestFixture.create().seasonPlanService().createDraft(
                SeasonPlanTestFixture.validPlanDraft("planner"), ActorRole.FAIRY_PLANNER, "planner");
        var plans = new JdbcSeasonPlanRepository(runtime, new ObjectMapper().findAndRegisterModules());
        plans.save(draft);
        draft.completeReadinessChecklistItem("calendar-reviewed", "coordinator", Instant.now());
        plans.save(draft);
        assertThat(plans.findById(draft.id()).orElseThrow().readinessChecklist()).anyMatch(item -> item.complete());
        var audit = new JdbcAuditLogRepository(runtime);
        var entry = new AuditLogEntry(UUID.randomUUID(), draft.id(), AuditAction.SEASON_PLAN_CREATED, "planner", Instant.now(), null);
        audit.append(entry);
        assertThat(audit.entriesFor(draft.id())).extracting(AuditLogEntry::id).containsExactly(entry.id());
    }

    @Test void restrictedRuntimeCanAppendAndReadButCannotRewriteMaterialHistory() {
        var plan = com.seasonplan.season.domain.SeasonPlan.createDraft(UUID.randomUUID(), "SPRING", java.util.List.of("north"), java.util.List.of(), java.util.Map.of("bloom", "early"), java.util.Map.of("temperature", 20.0), java.util.List.of(), "creator", Instant.now());
        var repository = new JdbcSeasonPlanRepository(runtime, new ObjectMapper().findAndRegisterModules());
        repository.save(plan);
        var original = repository.materialHistory(plan.id()).get(0);
        var loaded = repository.findById(plan.id()).orElseThrow();
        loaded.completeReadinessChecklistItem("manual", "other", Instant.now()); repository.save(loaded);
        assertThat(repository.materialHistory(plan.id())).containsExactly(original);
        assertThat(original.stamp().actorId()).isEqualTo("creator");
        for (String sql : java.util.List.of("update season_plan_material_revisions set snapshot = '{}'::jsonb", "delete from season_plan_material_revisions", "truncate season_plan_material_revisions", "alter table season_plan_material_revisions disable trigger all")) assertPermissionDenied(runtime, sql);
    }

    @Test void suppliedBootstrapProvisionsTheRestrictedRuntimeLoginAndRejectsOwnerReuse() throws Exception {
        var script = java.nio.file.Path.of("..", "infrastructure", "postgres", "010-runtime-role.sh").toAbsolutePath();
        postgres.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(script), "/tmp/010-runtime-role.sh");
        var provision = postgres.execInContainer("sh", "/tmp/010-runtime-role.sh");
        assertThat(provision.getExitCode()).as(provision.getStderr()).isZero();
        var provisioned = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "seasonplan_app", "bootstrap-test-only"));
        assertThat(provisioned.queryForObject("select current_user", String.class)).isEqualTo("seasonplan_app");
        assertThat(provisioned.queryForObject("select rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls from pg_roles where rolname = current_user", Boolean.class)).isFalse();
        assertThat(provisioned.queryForObject("select pg_has_role(current_user, 'seasonplan_owner', 'MEMBER')", Boolean.class)).isFalse();
        UUID id = UUID.randomUUID();
        provisioned.update("insert into audit_log_entries(id, action, actor_id) values (?, 'SEASON_PLAN_CREATED', 'runtime')", id);
        assertThat(provisioned.queryForObject("select count(*) from audit_log_entries where id = ?", Integer.class, id)).isEqualTo(1);
        for (String sql : java.util.List.of("delete from audit_log_entries", "truncate audit_log_entries", "alter table audit_log_entries disable trigger all", "set role seasonplan_owner", "create temporary table audit_log_entries(id integer)")) {
            assertPermissionDenied(provisioned, sql);
        }
        var reused = postgres.execInContainer("env", "DB_RUNTIME_USER=seasonplan_owner", "sh", "/tmp/010-runtime-role.sh");
        assertThat(reused.getExitCode()).isNotZero();
        assertThat(reused.getStderr()).contains("must differ");
    }

    @Test void runtimeHasNoElevatedFlagsOwnershipOrOwnerMembership() {
        assertThat(runtime.queryForObject("select rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls from pg_roles where rolname = current_user", Boolean.class)).isFalse();
        assertThat(runtime.queryForObject("select pg_has_role(current_user, 'seasonplan_owner', 'MEMBER')", Boolean.class)).isFalse();
        assertThat(runtime.queryForObject("select count(*) from pg_class c join pg_roles r on r.oid = c.relowner where r.rolname in (current_user, 'seasonplan_runtime') and c.relnamespace = 'public'::regnamespace", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "update audit_log_entries set comment = 'changed'",
        "delete from audit_log_entries",
        "truncate audit_log_entries",
        "alter table audit_log_entries disable trigger all",
        "drop trigger audit_log_entries_no_truncate on audit_log_entries",
        "drop table audit_log_entries",
        "alter function prevent_audit_log_mutation() rename to bypass_audit_guard",
        "create or replace function prevent_audit_log_mutation() returns trigger language plpgsql as 'begin return new; end'",
        "set session_replication_role = replica",
        "set role seasonplan_owner",
        "alter role privilege_test_app superuser",
        "grant seasonplan_owner to privilege_test_app",
        "create table public.runtime_shadow(id integer)",
        "create temporary table audit_log_entries(id integer)",
        "create schema runtime_shadow",
        "update databasechangelog set author = 'changed'"
    })
    void runtimeCannotChangeAuditGuardsEscalateOrShadowPersistence(String sql) {
        assertPermissionDenied(runtime, sql);
    }

    private static void assertPermissionDenied(JdbcTemplate identity, String sql) {
        assertThatThrownBy(() -> identity.execute(sql)).hasRootCauseInstanceOf(SQLException.class)
                .satisfies(error -> {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    assertThat(((SQLException) cause).getSQLState()).as(sql).isEqualTo("42501");
                });
    }
}
