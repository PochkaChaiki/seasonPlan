--liquibase formatted sql

--changeset seasonplan:008-review-outbox
alter table audit_log_entries drop constraint audit_log_entries_action_check;
alter table audit_log_entries add constraint audit_log_entries_action_check check (
    action in ('SEASON_PLAN_CREATED', 'SEASON_PLAN_UPDATED', 'SEASON_PLAN_SUBMITTED',
        'SEASON_PLAN_VERIFIED', 'SEASON_PLAN_APPROVED', 'SEASON_PLAN_RETURNED',
        'POLLEN_RESERVATION_CONFIRMED', 'POLLEN_RESERVATION_REJECTED',
        'MAGIC_TASK_CREATED', 'FAIRY_ASSIGNED', 'SEASON_MARKED_RITUAL_READY')
);
create table notification_outbox (
    event_id varchar(200) primary key,
    season_plan_id uuid not null references season_plans(id) on delete restrict,
    event_type varchar(80) not null,
    recipients jsonb not null,
    channels jsonb not null,
    message text not null,
    occurred_at timestamptz not null
);
