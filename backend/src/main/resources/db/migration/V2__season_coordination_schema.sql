create extension if not exists btree_gist;

create table season_plans (
    id uuid primary key,
    season_type varchar(80) not null,
    status varchar(32) not null,
    approval_comment text,
    natural_phenomenon_parameters jsonb not null,
    target_climate_metrics jsonb not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint season_plans_status_check check (
        status in ('DRAFT', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'RITUAL_READY', 'ACTIVE', 'COMPLETED', 'ARCHIVED')
    ),
    constraint season_plans_natural_parameters_object_check check (jsonb_typeof(natural_phenomenon_parameters) = 'object'),
    constraint season_plans_target_metrics_object_check check (jsonb_typeof(target_climate_metrics) = 'object')
);

create table season_plan_target_territories (
    id uuid primary key,
    season_plan_id uuid not null references season_plans(id) on delete cascade,
    territory varchar(160) not null,
    constraint season_plan_target_territories_unique unique (season_plan_id, territory)
);

create table season_plan_time_windows (
    id uuid primary key,
    season_plan_id uuid not null references season_plans(id) on delete cascade,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    constraint season_plan_time_windows_bounds_check check (starts_at < ends_at)
);

create index season_plan_time_windows_plan_idx on season_plan_time_windows(season_plan_id);

alter table season_plan_time_windows
    add constraint season_plan_time_windows_no_overlap
    exclude using gist (
        season_plan_id with =,
        tstzrange(starts_at, ends_at, '[)') with &&
    );

create table readiness_checklist_items (
    id uuid primary key,
    season_plan_id uuid not null references season_plans(id) on delete cascade,
    item_key varchar(120) not null,
    label varchar(240),
    complete boolean not null default false,
    completed_by varchar(120),
    completed_at timestamptz,
    constraint readiness_checklist_items_unique unique (season_plan_id, item_key),
    constraint readiness_checklist_items_completion_check check (
        (complete = false and completed_at is null) or (complete = true)
    )
);

create table pollen_reservations (
    id uuid primary key,
    season_plan_id uuid not null references season_plans(id) on delete cascade,
    requested_amount numeric(12, 3) not null,
    confirmed_amount numeric(12, 3),
    status varchar(32) not null,
    comment text,
    confirmed_by varchar(120),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint pollen_reservations_status_check check (status in ('PENDING', 'CONFIRMED', 'REJECTED')),
    constraint pollen_reservations_requested_amount_check check (requested_amount > 0),
    constraint pollen_reservations_confirmed_amount_check check (
        confirmed_amount is null or confirmed_amount > 0
    ),
    constraint pollen_reservations_confirmed_state_check check (
        (status = 'CONFIRMED' and confirmed_amount is not null and confirmed_by is not null)
        or (status <> 'CONFIRMED')
    )
);

create index pollen_reservations_plan_idx on pollen_reservations(season_plan_id);

create table magic_tasks (
    id uuid primary key,
    season_plan_id uuid not null references season_plans(id) on delete cascade,
    title varchar(240) not null,
    description text,
    required_talent varchar(120) not null,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    status varchar(32) not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint magic_tasks_status_check check (status in ('TODO', 'READY', 'BLOCKED')),
    constraint magic_tasks_time_window_check check (starts_at < ends_at)
);

create index magic_tasks_plan_idx on magic_tasks(season_plan_id);

create table fairy_assignments (
    id uuid primary key,
    magic_task_id uuid not null references magic_tasks(id) on delete cascade,
    fairy_id varchar(120) not null,
    role varchar(120) not null,
    talent varchar(120) not null,
    status varchar(32) not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fairy_assignments_status_check check (status in ('PROPOSED', 'CONFIRMED')),
    constraint fairy_assignments_unique_role unique (magic_task_id, fairy_id, role)
);

create index fairy_assignments_task_idx on fairy_assignments(magic_task_id);
create index fairy_assignments_fairy_idx on fairy_assignments(fairy_id);

create table ritual_sessions (
    id uuid primary key,
    season_plan_id uuid not null references season_plans(id) on delete restrict,
    validation_status varchar(32) not null,
    validation_result jsonb not null default '{}'::jsonb,
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ritual_sessions_validation_status_check check (validation_status in ('PENDING', 'VALID', 'INVALID')),
    constraint ritual_sessions_result_object_check check (jsonb_typeof(validation_result) = 'object')
);

create table climate_metrics (
    id uuid primary key,
    season_plan_id uuid references season_plans(id) on delete cascade,
    metric_key varchar(120) not null,
    metric_value numeric(14, 4) not null,
    unit varchar(40) not null,
    source varchar(120) not null,
    measured_at timestamptz not null,
    received_at timestamptz not null default now()
);

create index climate_metrics_plan_measured_idx on climate_metrics(season_plan_id, measured_at desc);

create table magic_incidents (
    id uuid primary key,
    season_plan_id uuid not null references season_plans(id) on delete restrict,
    severity varchar(32) not null,
    status varchar(32) not null,
    title varchar(240) not null,
    description text,
    opened_by varchar(120) not null,
    closed_by varchar(120),
    opened_at timestamptz not null default now(),
    closed_at timestamptz,
    constraint magic_incidents_severity_check check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    constraint magic_incidents_status_check check (status in ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    constraint magic_incidents_closed_state_check check (
        (status in ('RESOLVED', 'CLOSED') and closed_at is not null)
        or (status not in ('RESOLVED', 'CLOSED'))
    )
);

create index magic_incidents_plan_status_idx on magic_incidents(season_plan_id, status);

create table correction_actions (
    id uuid primary key,
    magic_incident_id uuid not null references magic_incidents(id) on delete cascade,
    action_type varchar(120) not null,
    description text not null,
    status varchar(32) not null,
    assigned_to varchar(120),
    due_at timestamptz,
    completed_at timestamptz,
    created_by varchar(120) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint correction_actions_status_check check (status in ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'))
);

create index correction_actions_incident_idx on correction_actions(magic_incident_id);

create table season_reports (
    id uuid primary key,
    season_plan_id uuid not null references season_plans(id) on delete restrict,
    report_type varchar(32) not null,
    status varchar(32) not null,
    content jsonb not null,
    generated_by varchar(120) not null,
    generated_at timestamptz not null default now(),
    exported_at timestamptz,
    constraint season_reports_type_check check (report_type in ('PDF', 'HTML', 'JSON')),
    constraint season_reports_status_check check (status in ('GENERATED', 'EXPORTED')),
    constraint season_reports_content_object_check check (jsonb_typeof(content) = 'object')
);

create index season_reports_plan_idx on season_reports(season_plan_id);

create table audit_log_entries (
    id uuid primary key,
    season_plan_id uuid references season_plans(id) on delete restrict,
    action varchar(80) not null,
    actor_id varchar(120) not null,
    created_at timestamptz not null default now(),
    comment text,
    details jsonb not null default '{}'::jsonb,
    constraint audit_log_entries_action_check check (
        action in (
            'SEASON_PLAN_CREATED',
            'SEASON_PLAN_UPDATED',
            'SEASON_PLAN_SUBMITTED',
            'SEASON_PLAN_APPROVED',
            'SEASON_PLAN_RETURNED',
            'POLLEN_RESERVATION_CONFIRMED',
            'POLLEN_RESERVATION_REJECTED',
            'MAGIC_TASK_CREATED',
            'FAIRY_ASSIGNED',
            'SEASON_MARKED_RITUAL_READY'
        )
    ),
    constraint audit_log_entries_details_object_check check (jsonb_typeof(details) = 'object')
);

create index audit_log_entries_plan_created_idx on audit_log_entries(season_plan_id, created_at);
create index audit_log_entries_action_idx on audit_log_entries(action);

create or replace function prevent_audit_log_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'audit_log_entries are immutable';
end;
$$;

create trigger audit_log_entries_no_update
before update on audit_log_entries
for each row execute function prevent_audit_log_mutation();

create trigger audit_log_entries_no_delete
before delete on audit_log_entries
for each row execute function prevent_audit_log_mutation();
