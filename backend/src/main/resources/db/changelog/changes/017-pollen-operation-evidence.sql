--liquibase formatted sql

--changeset seasonplan:017-pollen-operation-evidence
create table pollen_operations (
    id uuid primary key,
    season_plan_id uuid not null,
    material_revision bigint not null,
    payload jsonb not null check (jsonb_typeof(payload) = 'object'),
    state text not null check (state in ('DISPATCHED','UNKNOWN','RECEIVED','APPLIED','OBSOLETE','DENIED')),
    receipt jsonb,
    denial_field text,
    created_at timestamptz not null,
    foreign key (season_plan_id, material_revision) references season_plan_material_revisions(season_plan_id, material_revision),
    check ((payload ->> 'planId')::uuid = season_plan_id),
    check ((payload ->> 'materialRevision')::bigint = material_revision),
    check (payload ->> 'kind' in ('REQUEST','CONFIRM','REJECT')),
    check ((state in ('RECEIVED','APPLIED','OBSOLETE')) = (receipt is not null)),
    check ((state = 'DENIED') = (denial_field is not null))
);
create index pollen_operations_unresolved on pollen_operations(season_plan_id, state);
create table pollen_reservation_bindings (
    reservation_id uuid primary key,
    season_plan_id uuid not null,
    material_revision bigint not null,
    request_operation_id uuid not null unique references pollen_operations(id),
    foreign key (season_plan_id, material_revision) references season_plan_material_revisions(season_plan_id, material_revision)
);

--changeset seasonplan:017-pollen-evidence-guards splitStatements:false
create function protect_pollen_operation_evidence() returns trigger language plpgsql as $$
begin
    if tg_op = 'DELETE' or new.id is distinct from old.id or new.season_plan_id is distinct from old.season_plan_id
       or new.material_revision is distinct from old.material_revision or new.payload is distinct from old.payload
       or new.created_at is distinct from old.created_at
       or (old.receipt is not null and new.receipt is distinct from old.receipt)
       or (old.state in ('APPLIED','OBSOLETE','DENIED') and new is distinct from old) then
        raise exception 'pollen intent and known receipt are immutable' using errcode = '42501';
    end if;
    return new;
end $$;

--changeset seasonplan:017-pollen-evidence-privileges
create trigger pollen_operations_evidence before update or delete on pollen_operations
for each row execute function protect_pollen_operation_evidence();
create trigger pollen_operations_no_truncate before truncate on pollen_operations
for each statement execute function prevent_material_revision_mutation();
create trigger pollen_bindings_no_change before update or delete on pollen_reservation_bindings
for each row execute function prevent_material_revision_mutation();
create trigger pollen_bindings_no_truncate before truncate on pollen_reservation_bindings
for each statement execute function prevent_material_revision_mutation();
revoke all on pollen_operations, pollen_reservation_bindings from public, seasonplan_runtime;
grant select, insert on pollen_operations, pollen_reservation_bindings to seasonplan_runtime;
grant update(state, receipt, denial_field) on pollen_operations to seasonplan_runtime;

--changeset seasonplan:017-pollen-binding-evidence splitStatements:false
create function validate_pollen_binding_evidence() returns trigger language plpgsql as $$
declare operation pollen_operations%rowtype;
begin
    select * into operation from pollen_operations where id = new.request_operation_id;
    if not found or operation.payload ->> 'kind' is distinct from 'REQUEST'
       or operation.season_plan_id is distinct from new.season_plan_id
       or operation.material_revision is distinct from new.material_revision
       or operation.receipt is null or (operation.receipt ->> 'id')::uuid is distinct from new.reservation_id
       or (operation.receipt ->> 'seasonPlanId')::uuid is distinct from new.season_plan_id then
        raise exception 'binding must reference matching accepted request evidence' using errcode = '23514';
    end if;
    return new;
end $$;

--changeset seasonplan:017-pollen-binding-trigger
create trigger pollen_binding_evidence before insert on pollen_reservation_bindings
for each row execute function validate_pollen_binding_evidence();
