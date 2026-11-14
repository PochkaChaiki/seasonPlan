--liquibase formatted sql

--changeset seasonplan:009-approval-conditions
alter table season_plans add column approval_conditions jsonb not null default '[]'::jsonb;
alter table season_plans add constraint season_plans_approval_conditions_array_check
    check (jsonb_typeof(approval_conditions) = 'array');
