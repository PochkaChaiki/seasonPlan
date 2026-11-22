--liquibase formatted sql

--changeset seasonplan:015-runtime-database-privileges splitStatements:false
-- The DBA can pre-create this NOLOGIN role when the migration owner lacks CREATEROLE.
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'seasonplan_runtime') then
        create role seasonplan_runtime nologin nosuperuser nocreatedb nocreaterole noreplication nobypassrls;
    end if;
    if exists (select 1 from pg_roles where rolname = 'seasonplan_runtime'
               and (rolcanlogin or rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls))
       or exists (select 1 from pg_auth_members where member = 'seasonplan_runtime'::regrole)
       or exists (select 1 from pg_class where relowner = 'seasonplan_runtime'::regrole)
       or exists (select 1 from pg_namespace where nspowner = 'seasonplan_runtime'::regrole)
       or exists (select 1 from pg_database where datdba = 'seasonplan_runtime'::regrole) then
        raise exception 'seasonplan_runtime must be an unprivileged, non-owning NOLOGIN role without parent roles';
    end if;
    -- Temporary relations can shadow unqualified persistence/audit table names.
    execute format('revoke temporary, create on database %I from public, seasonplan_runtime', current_database());
end $$;

revoke create on schema public from public;
revoke all on schema public from seasonplan_runtime;
grant usage on schema public to seasonplan_runtime;
revoke all on all tables in schema public from seasonplan_runtime;
revoke all on all sequences in schema public from seasonplan_runtime;
revoke all on audit_log_entries from public;

-- Explicit list: later migrations must grant only the permissions their tables need.
grant select, insert, update, delete on
    season_plans, season_plan_target_territories, season_plan_time_windows, readiness_checklist_items,
    pollen_reservations, magic_tasks, fairy_assignments, ritual_sessions,
    climate_metrics, magic_incidents, correction_actions, season_reports,
    users, local_credentials, user_roles, spring_session, spring_session_attributes,
    notification_outbox, notification_outbox_deliveries, notification_inbox,
    ritual_session_participants, ritual_session_events
to seasonplan_runtime;
grant select, insert on audit_log_entries to seasonplan_runtime;
