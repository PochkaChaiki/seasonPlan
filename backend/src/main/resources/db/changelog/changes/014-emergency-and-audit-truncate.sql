--liquibase formatted sql

--changeset seasonplan:014-emergency-status-and-audit
alter table season_plans drop constraint season_plans_status_check;
alter table season_plans add constraint season_plans_status_check check (
    status in ('DRAFT','SUBMITTED','VERIFIED','APPROVED','RITUAL_READY','ACTIVE',
               'CORRECTION','EMERGENCY','COMPLETED','ARCHIVED','CANCELLED')
);
alter table audit_log_entries drop constraint audit_log_entries_action_check;
alter table audit_log_entries add constraint audit_log_entries_action_check check (
    action in ('SEASON_PLAN_CREATED','SEASON_PLAN_UPDATED','SEASON_PLAN_SUBMITTED','SEASON_PLAN_VERIFIED',
      'SEASON_PLAN_APPROVED','SEASON_PLAN_RETURNED','POLLEN_RESERVATION_CONFIRMED','POLLEN_RESERVATION_REJECTED',
      'MAGIC_TASK_CREATED','MAGIC_TASK_UPDATED','FAIRY_ASSIGNED','SEASON_MARKED_RITUAL_READY',
      'RITUAL_SESSION_STARTED','RITUAL_EVENT_RECORDED','RITUAL_SESSION_FINISHED','SEASON_PLAN_ACTIVATED',
      'CLIMATE_METRIC_RECORDED','MAGIC_INCIDENT_REGISTERED','MAGIC_INCIDENT_RESOLVED','MAGIC_INCIDENT_REOPENED',
      'INCIDENT_SEVERITY_CHANGED','CORRECTION_ACTION_CREATED','CORRECTION_ACTION_CHANGED',
      'SEASON_REPORT_GENERATED','SEASON_REPORT_APPROVED','SEASON_PLAN_COMPLETED','SEASON_PLAN_ARCHIVED',
      'POLLEN_CONSUMPTION_RECORDED','SEASON_EMERGENCY_ENTERED')
);

--changeset seasonplan:014-audit-truncate-guard
create trigger audit_log_entries_no_truncate
before truncate on audit_log_entries
for each statement execute function prevent_audit_log_mutation();
