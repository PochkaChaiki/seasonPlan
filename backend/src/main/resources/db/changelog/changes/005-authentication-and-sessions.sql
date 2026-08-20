--liquibase formatted sql

--changeset seasonplan:005-authentication-schema
create table users (
    id uuid primary key,
    username varchar(100) not null unique,
    display_name varchar(200) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    constraint users_username_check check (username ~ '^[a-z0-9][a-z0-9._-]{0,99}$')
);

create table local_credentials (
    user_id uuid primary key references users(id) on delete restrict,
    password_hash varchar(255) not null,
    updated_at timestamptz not null default now(),
    constraint local_credentials_password_hash_check check (password_hash like '{%}%')
);

create table user_roles (
    user_id uuid not null references users(id) on delete restrict,
    role varchar(64) not null,
    primary key (user_id, role),
    constraint user_roles_catalog_check check (role in (
        'FAIRY_PLANNER', 'CHIEF_GUARDIAN', 'TALENT_COORDINATOR', 'POLLEN_GUARDIAN',
        'CLIMATE_BALANCER', 'MAGIC_ANOMALY_CONTROLLER', 'FAIRY_PARTICIPANT'
    ))
);

--changeset seasonplan:006-spring-session-jdbc
create table spring_session (
    primary_id char(36) not null,
    session_id char(36) not null,
    creation_time bigint not null,
    last_access_time bigint not null,
    max_inactive_interval integer not null,
    expiry_time bigint not null,
    principal_name varchar(100),
    constraint spring_session_pk primary key (primary_id)
);
create unique index spring_session_ix1 on spring_session (session_id);
create index spring_session_ix2 on spring_session (expiry_time);
create index spring_session_ix3 on spring_session (principal_name);

create table spring_session_attributes (
    session_primary_id char(36) not null,
    attribute_name varchar(200) not null,
    attribute_bytes bytea not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id) references spring_session(primary_id) on delete cascade
);
