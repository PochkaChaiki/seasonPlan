package com.seasonplan.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationContractTest {
    @Test
    void liquibaseChangelogDefinesRequiredTablesAndAuditImmutability() throws IOException {
        String masterChangelog = Files.readString(Path.of("src/main/resources/db/changelog/db.changelog-master.yaml"));
        assertThat(masterChangelog).contains(
                "db/changelog/changes/001-initial-schema.sql",
                "db/changelog/changes/002-season-coordination-schema.sql"
        );

        String migration = Files.readString(Path.of("src/main/resources/db/changelog/changes/002-season-coordination-schema.sql"));

        assertThat(migration).contains(
                "--liquibase formatted sql",
                "--changeset seasonplan:002-season-coordination-schema",
                "create table season_plans",
                "create table season_plan_time_windows",
                "create table season_plan_target_territories",
                "create table pollen_reservations",
                "create table magic_tasks",
                "create table fairy_assignments",
                "create table readiness_checklist_items",
                "create table climate_metrics",
                "create table ritual_sessions",
                "create table magic_incidents",
                "create table correction_actions",
                "create table season_reports",
                "create table audit_log_entries",
                "prevent_audit_log_mutation",
                "audit_log_entries_no_update",
                "audit_log_entries_no_delete"
        );

        List<String> requiredStatuses = List.of(
                "DRAFT", "SUBMITTED", "VERIFIED", "APPROVED", "RITUAL_READY", "ACTIVE", "COMPLETED", "ARCHIVED"
        );

        for (String status : requiredStatuses) {
            assertThat(migration).contains("'" + status + "'");
        }
    }

    @Test
    void authenticationAdministrationMigrationAddsASeparateSystemAdministratorFlag() throws IOException {
        String masterChangelog = Files.readString(Path.of("src/main/resources/db/changelog/db.changelog-master.yaml"));
        String migration = Files.readString(Path.of("src/main/resources/db/changelog/changes/007-system-administrators.sql"));

        assertThat(masterChangelog).contains("db/changelog/changes/007-system-administrators.sql");
        assertThat(migration).contains(
                "alter table users add column system_admin boolean not null default false",
                "create unique index users_one_system_admin",
                "where system_admin"
        );
    }
}
