--liquibase formatted sql

--changeset seasonplan:013-execution-readiness
alter table season_plans add column execution_rules jsonb;
alter table season_plans add constraint season_plans_execution_rules_object_check check (execution_rules is null or jsonb_typeof(execution_rules) = 'object');
alter table magic_tasks add column category text;
alter table magic_tasks drop constraint magic_tasks_status_check;
alter table magic_tasks add constraint magic_tasks_status_check check (status in ('TODO', 'READY', 'BLOCKED', 'COMPLETED', 'CANCELLED'));
alter table fairy_assignments add column confirmed_at timestamptz;
alter table audit_log_entries drop constraint audit_log_entries_action_check;
alter table audit_log_entries add constraint audit_log_entries_action_check check (
    action in ('SEASON_PLAN_CREATED','SEASON_PLAN_UPDATED','SEASON_PLAN_SUBMITTED','SEASON_PLAN_VERIFIED',
        'SEASON_PLAN_APPROVED','SEASON_PLAN_RETURNED','POLLEN_RESERVATION_CONFIRMED','POLLEN_RESERVATION_REJECTED',
        'MAGIC_TASK_CREATED','MAGIC_TASK_UPDATED','FAIRY_ASSIGNED','SEASON_MARKED_RITUAL_READY',
        'RITUAL_SESSION_STARTED','RITUAL_EVENT_RECORDED','RITUAL_SESSION_FINISHED','SEASON_PLAN_ACTIVATED',
        'CLIMATE_METRIC_RECORDED','MAGIC_INCIDENT_REGISTERED','MAGIC_INCIDENT_RESOLVED','MAGIC_INCIDENT_REOPENED',
        'INCIDENT_SEVERITY_CHANGED','CORRECTION_ACTION_CREATED','CORRECTION_ACTION_CHANGED',
        'SEASON_REPORT_GENERATED','SEASON_REPORT_APPROVED','SEASON_PLAN_COMPLETED','SEASON_PLAN_ARCHIVED',
        'POLLEN_CONSUMPTION_RECORDED')
);

--changeset seasonplan:013-execution-rules-immutable splitStatements:false
create function season_plan_execution_rules_immutable() returns trigger language plpgsql as $$ begin
    if old.execution_rules is not null and new.execution_rules is distinct from old.execution_rules then
        raise exception 'pinned execution rules are immutable';
    end if;
    return new;
end $$;
create trigger season_plan_execution_rules_immutable_before before update of execution_rules on season_plans
for each row execute function season_plan_execution_rules_immutable();
