--liquibase formatted sql

--changeset seasonplan:007-system-administrators
alter table users add column system_admin boolean not null default false;
create unique index users_one_system_admin on users (system_admin) where system_admin;
