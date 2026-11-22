--liquibase formatted sql

--changeset seasonplan:018-consumption-operation-evidence
create table pollen_consumption_operations (
    id uuid primary key,
    season_plan_id uuid not null,
    material_revision bigint not null,
    intent jsonb not null,
    state text not null check (state in ('DISPATCHED','UNKNOWN','RECEIVED','APPLIED','QUARANTINED','DENIED')),
    receipt jsonb,
    check (jsonb_typeof(intent) = 'object'
        and intent ->> 'id' is not null and intent ->> 'planId' is not null
        and intent ->> 'materialRevision' is not null and intent ->> 'clientKey' is not null
        and intent ->> 'amount' is not null and intent ->> 'actorId' is not null
        and intent ->> 'createdAt' is not null and intent ->> 'state' is not null),
    check ((intent ->> 'clientKey')::uuid is not null),
    check (jsonb_typeof(intent -> 'createdAt') in ('string','number')),
    check (receipt is null or (jsonb_typeof(receipt) = 'object'
        and receipt ->> 'planId' is not null and receipt ->> 'clientKey' is not null
        and receipt ->> 'amount' is not null and receipt ->> 'reservationId' is not null
        and receipt ->> 'confirmedGrams' is not null and receipt ->> 'actualGrams' is not null
        and receipt ->> 'version' is not null)),
    foreign key (season_plan_id, material_revision) references season_plan_material_revisions(season_plan_id, material_revision),
    check ((intent ->> 'id')::uuid = id),
    check ((intent ->> 'planId')::uuid = season_plan_id),
    check ((intent ->> 'materialRevision')::bigint = material_revision),
    check ((intent ->> 'amount')::numeric > 0),
    check (length(intent ->> 'actorId') > 0),
    check ((state in ('RECEIVED','APPLIED','QUARANTINED')) = (receipt is not null)),
    check (state <> 'APPLIED' or (
        receipt ->> 'planId' = intent ->> 'planId' and receipt ->> 'clientKey' = intent ->> 'clientKey'
        and (receipt ->> 'amount')::numeric = (intent ->> 'amount')::numeric
        and (intent ->> 'expectedReservationId' is null or receipt ->> 'reservationId' = intent ->> 'expectedReservationId')))
);
create unique index pollen_consumption_client_key on pollen_consumption_operations (season_plan_id, (intent ->> 'clientKey'));
create index pollen_consumption_unresolved on pollen_consumption_operations(season_plan_id, state);

--changeset seasonplan:018-consumption-evidence-guard splitStatements:false
create function protect_consumption_evidence() returns trigger language plpgsql as $$
begin
    if tg_op = 'DELETE' or new.id is distinct from old.id or new.season_plan_id is distinct from old.season_plan_id
       or new.material_revision is distinct from old.material_revision or new.intent is distinct from old.intent
       or (old.receipt is not null and new.receipt is distinct from old.receipt)
       or (old.state in ('APPLIED','QUARANTINED','DENIED') and new is distinct from old)
       or (old.state = 'RECEIVED' and new.state not in ('RECEIVED','APPLIED','QUARANTINED')) then
        raise exception 'consumption intent and receipt are immutable' using errcode = '42501';
    end if;
    return new;
end $$;

--changeset seasonplan:018-consumption-evidence-privileges
create trigger consumption_evidence before update or delete on pollen_consumption_operations
for each row execute function protect_consumption_evidence();
create trigger consumption_no_truncate before truncate on pollen_consumption_operations
for each statement execute function prevent_material_revision_mutation();
revoke all on pollen_consumption_operations from public, seasonplan_runtime;
grant select, insert on pollen_consumption_operations to seasonplan_runtime;
grant update(state, receipt) on pollen_consumption_operations to seasonplan_runtime;
