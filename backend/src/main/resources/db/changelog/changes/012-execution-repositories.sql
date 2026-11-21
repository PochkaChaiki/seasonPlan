--liquibase formatted sql

--changeset seasonplan:012-execution-repositories splitStatements:false
alter table climate_metrics add column canonical boolean;
alter table climate_metrics add column source_event_id text;
alter table climate_metrics add column target_value numeric;
alter table climate_metrics add column tolerance numeric;
alter table climate_metrics alter column metric_value type numeric;
alter table climate_metrics add constraint climate_metrics_canonical_check check (canonical is null or (canonical and season_plan_id is not null and source_event_id is not null and btrim(source_event_id) <> '' and target_value is not null and tolerance > 0 and btrim(source) <> '' and metric_key in ('temperature','humidity','pollen_density') and ((metric_key = 'temperature' and unit = 'CELSIUS') or (metric_key = 'humidity' and unit = 'PERCENT') or (metric_key = 'pollen_density' and unit = 'G_PER_M3'))) is true);
create unique index climate_metrics_import_key_unique on climate_metrics(source, source_event_id) where canonical;

alter table magic_incidents add column canonical boolean;
alter table magic_incidents add column incident_type text;
alter table magic_incidents add column cause text;
alter table magic_incidents add column consequences text;
alter table magic_incidents alter column title drop not null;
alter table magic_incidents drop constraint magic_incidents_closed_state_check;
alter table magic_incidents add constraint magic_incidents_closed_state_check check (canonical is true or ((status in ('RESOLVED', 'CLOSED') and closed_at is not null) or status not in ('RESOLVED', 'CLOSED')));
alter table magic_incidents add constraint magic_incidents_canonical_check check ((canonical is null or (canonical and incident_type in ('RITUAL','POLLEN','CLIMATE','ASSIGNMENT','SECURITY') and cause is not null and btrim(cause) <> '' and consequences is not null and btrim(consequences) <> '' and btrim(opened_by) <> '')) is true);
alter table magic_incidents add constraint magic_incidents_id_plan_unique unique (id, season_plan_id);

alter table correction_actions add column canonical boolean;
alter table correction_actions add column season_plan_id uuid references season_plans(id) on delete restrict;
alter table correction_actions add column deduplication_key text;
alter table correction_actions add column cancellation_reason text;
alter table correction_actions alter column magic_incident_id drop not null;
alter table correction_actions alter column action_type drop not null;
alter table correction_actions alter column created_by drop not null;
alter table correction_actions add constraint correction_actions_incident_plan_fk foreign key (magic_incident_id, season_plan_id) references magic_incidents(id, season_plan_id);
alter table correction_actions add constraint correction_actions_canonical_check check ((canonical is null or (canonical and season_plan_id is not null and deduplication_key is not null and btrim(deduplication_key) <> '' and btrim(description) <> '' and assigned_to is not null and btrim(assigned_to) <> '' and due_at is not null and (status <> 'CANCELLED' or (cancellation_reason is not null and btrim(cancellation_reason) <> '')))) is true);
create unique index correction_actions_dedupe_unique on correction_actions(deduplication_key) where canonical;

alter table ritual_sessions add column canonical boolean;
alter table ritual_sessions add column attempt integer;
alter table ritual_sessions add column starter text;
alter table ritual_sessions add column result text;
alter table ritual_sessions add column failure_code text;
alter table ritual_sessions add column failure_message text;
alter table ritual_sessions add column participants_sealed boolean not null default false;
alter table ritual_sessions add constraint ritual_sessions_canonical_check check ((canonical is null or (canonical and attempt > 0 and starter is not null and btrim(starter) <> '' and started_at is not null and (completed_at is null or completed_at >= started_at) and ((result is null and completed_at is null and failure_code is null and failure_message is null) or (result = 'SUCCESS' and completed_at is not null and failure_code is null and failure_message is null) or (result = 'FAILED' and completed_at is not null and failure_code is not null and btrim(failure_code) <> '' and failure_message is not null and btrim(failure_message) <> '')))) is true);
create unique index ritual_sessions_attempt_unique on ritual_sessions(season_plan_id, attempt) where canonical;
create unique index ritual_sessions_active_unique on ritual_sessions(season_plan_id) where canonical and result is null;
create unique index ritual_sessions_success_unique on ritual_sessions(season_plan_id) where canonical and result = 'SUCCESS';
create table ritual_session_participants (session_id uuid not null references ritual_sessions(id) on delete restrict, position integer not null, fairy_id text not null, role text not null, confirmed_at timestamptz not null, primary key(session_id, position));
create table ritual_session_events (session_id uuid not null references ritual_sessions(id) on delete restrict, position integer not null, occurred_at timestamptz not null, event_type text not null, payload text not null, primary key(session_id, position));

create function execution_require_canonical_insert() returns trigger language plpgsql as $$ begin
    if new.canonical is distinct from true then raise exception 'canonical execution metadata is required for new rows'; end if;
    return new;
end $$;
create trigger climate_metrics_require_canonical before insert on climate_metrics for each row execute function execution_require_canonical_insert();
create trigger magic_incidents_require_canonical before insert on magic_incidents for each row execute function execution_require_canonical_insert();
create trigger correction_actions_require_canonical before insert on correction_actions for each row execute function execution_require_canonical_insert();
create trigger ritual_sessions_require_canonical before insert on ritual_sessions for each row execute function execution_require_canonical_insert();

create function execution_immutable_metric() returns trigger language plpgsql as $$ begin
    raise exception 'climate metrics are immutable';
end $$;
create trigger climate_metrics_immutable before update or delete on climate_metrics for each row when (old.canonical) execute function execution_immutable_metric();

create function execution_immutable_incident_identity() returns trigger language plpgsql as $$ begin
    if tg_op = 'DELETE' or new.id <> old.id or new.season_plan_id <> old.season_plan_id or new.incident_type <> old.incident_type or new.cause <> old.cause or new.consequences <> old.consequences or new.opened_by <> old.opened_by or new.opened_at <> old.opened_at or new.canonical is distinct from old.canonical then
        raise exception 'incident identity and original details are immutable';
    end if;
    return new;
end $$;
create trigger magic_incidents_immutable before update or delete on magic_incidents for each row when (old.canonical) execute function execution_immutable_incident_identity();

create function execution_immutable_action() returns trigger language plpgsql as $$ begin
    if tg_op = 'DELETE' or new.id <> old.id or new.season_plan_id <> old.season_plan_id or new.magic_incident_id is distinct from old.magic_incident_id or new.deduplication_key <> old.deduplication_key or new.description <> old.description or new.assigned_to <> old.assigned_to or new.due_at <> old.due_at or new.canonical is distinct from old.canonical or (old.status in ('DONE','CANCELLED') and new is distinct from old) or (new.status <> old.status and not ((old.status = 'OPEN' and new.status in ('IN_PROGRESS','CANCELLED')) or (old.status = 'IN_PROGRESS' and new.status in ('OPEN','DONE','CANCELLED')))) then
        raise exception 'correction action identity and terminal actions are immutable';
    end if;
    return new;
end $$;
create trigger correction_actions_immutable before update or delete on correction_actions for each row when (old.canonical) execute function execution_immutable_action();

create function ritual_immutable_session() returns trigger language plpgsql as $$ begin
    if tg_op = 'DELETE' or old.result is not null or new.id <> old.id or new.season_plan_id <> old.season_plan_id or new.attempt <> old.attempt or new.starter <> old.starter or new.started_at <> old.started_at or new.canonical is distinct from old.canonical or (old.participants_sealed and not new.participants_sealed) then
        raise exception 'ritual session identity and completed sessions are immutable';
    end if;
    return new;
end $$;
create trigger ritual_session_immutable before update or delete on ritual_sessions for each row when (old.canonical) execute function ritual_immutable_session();
create function ritual_success_exclusivity() returns trigger language plpgsql as $$ begin
    perform id from season_plans where id = new.season_plan_id for update;
    if new.result is null and exists (select 1 from ritual_sessions where canonical and season_plan_id = new.season_plan_id and result = 'SUCCESS' and id <> new.id) then
        raise exception 'a successful ritual already exists';
    end if;
    if new.result = 'SUCCESS' and exists (select 1 from ritual_sessions where canonical and season_plan_id = new.season_plan_id and result is null and id <> new.id) then
        raise exception 'another ritual is in progress';
    end if;
    return new;
end $$;
create trigger ritual_success_exclusivity_before before insert or update of result on ritual_sessions for each row when (new.canonical) execute function ritual_success_exclusivity();
create function ritual_validate_snapshot() returns trigger language plpgsql as $$ begin
    if not (select participants_sealed from ritual_sessions where id = new.id) or not exists (select 1 from ritual_session_participants where session_id = new.id) then
        raise exception 'ritual participant snapshot must be complete and sealed';
    end if;
    if exists (select 1 from ritual_sessions s join ritual_session_events e on e.session_id = s.id where s.id = new.id and s.completed_at < e.occurred_at) then
        raise exception 'ritual completion precedes an event';
    end if;
    return null;
end $$;
create constraint trigger ritual_validate_snapshot_after after insert or update on ritual_sessions deferrable initially deferred for each row when (new.canonical) execute function ritual_validate_snapshot();
create function ritual_immutable_child() returns trigger language plpgsql as $$ declare finished boolean; previous_time timestamptz; next_position integer; begin
    if tg_op <> 'INSERT' then raise exception 'ritual snapshots and events are immutable'; end if;
    select result is not null, started_at into finished, previous_time from ritual_sessions where id = new.session_id for update;
    if finished then raise exception 'completed ritual is immutable'; end if;
    select count(*) into next_position from ritual_session_events where session_id = new.session_id;
    if next_position > 0 then select occurred_at into previous_time from ritual_session_events where session_id = new.session_id order by position desc limit 1; end if;
    if new.position <> next_position or new.occurred_at < previous_time or btrim(new.event_type) = '' then raise exception 'ritual events must be contiguous and ordered'; end if;
    return new;
end $$;
create function ritual_participant_insert_guard() returns trigger language plpgsql as $$ declare sealed boolean; begin
    if tg_op <> 'INSERT' then raise exception 'ritual participants are immutable'; end if;
    select participants_sealed into sealed from ritual_sessions where id = new.session_id for update;
    if sealed then raise exception 'ritual participants are sealed'; end if;
    if new.position <> (select count(*) from ritual_session_participants where session_id = new.session_id) or btrim(new.fairy_id) = '' or btrim(new.role) = '' then raise exception 'invalid ritual participant snapshot'; end if;
    return new;
end $$;
create trigger ritual_participants_immutable before insert or update or delete on ritual_session_participants for each row execute function ritual_participant_insert_guard();
create trigger ritual_events_immutable before insert or update or delete on ritual_session_events for each row execute function ritual_immutable_child();
