--liquibase formatted sql

--changeset seasonplan:016-season-material-revisions
alter table season_plans add column material_revision bigint not null default 0 check (material_revision >= 0);
alter table season_plans add column material_revision_actor text not null default 'system:migration';
alter table season_plans add column material_revision_at timestamptz not null default clock_timestamp();
alter table season_plans add column material_revision_reason text not null default 'LEGACY_BASELINE'
    check (material_revision_reason in ('CREATE', 'EDIT', 'LEGACY_BASELINE'));

create table season_plan_material_revisions (
    season_plan_id uuid not null references season_plans(id),
    material_revision bigint not null check (material_revision >= 0),
    snapshot jsonb not null,
    primary key (season_plan_id, material_revision),
    check ((snapshot #>> '{stamp,number}')::bigint = material_revision),
    check ((snapshot ->> 'seasonPlanId')::uuid = season_plan_id),
    check (snapshot #>> '{stamp,reason}' in ('CREATE', 'EDIT', 'LEGACY_BASELINE'))
);

-- Capture current data only. No claim is made about earlier edits, decisions or funding.
insert into season_plan_material_revisions(season_plan_id, material_revision, snapshot)
select p.id, 0, jsonb_build_object(
    'seasonPlanId', p.id,
    'stamp', jsonb_build_object('number', 0, 'actorId', 'system:migration', 'capturedAt', p.material_revision_at, 'reason', 'LEGACY_BASELINE'),
    'material', jsonb_build_object('seasonType', p.season_type,
        'targetTerritories', coalesce((select jsonb_agg(t.territory order by t.territory) from season_plan_target_territories t where t.season_plan_id = p.id), '[]'::jsonb),
        'timeWindows', coalesce((select jsonb_agg(jsonb_build_object('start', w.starts_at::timestamp, 'end', w.ends_at::timestamp) order by w.starts_at, w.ends_at) from season_plan_time_windows w where w.season_plan_id = p.id), '[]'::jsonb),
        'naturalPhenomenonParameters', p.natural_phenomenon_parameters, 'targetClimateMetrics', p.target_climate_metrics),
    'executionRules', p.execution_rules)
from season_plans p;

--changeset seasonplan:016-material-history-immutability splitStatements:false
create function prevent_material_revision_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'material revision history is append-only' using errcode = '42501';
end;
$$;

--changeset seasonplan:016-material-history-grants
create trigger material_revisions_no_change before update or delete on season_plan_material_revisions
for each row execute function prevent_material_revision_mutation();
create trigger material_revisions_no_truncate before truncate on season_plan_material_revisions
for each statement execute function prevent_material_revision_mutation();
revoke all on season_plan_material_revisions from public, seasonplan_runtime;
grant select, insert on season_plan_material_revisions to seasonplan_runtime;
