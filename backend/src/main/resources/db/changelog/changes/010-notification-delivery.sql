--liquibase formatted sql

--changeset seasonplan:010-notification-delivery
create table notification_outbox_deliveries (
    event_id varchar(200) not null references notification_outbox(event_id) on delete restrict,
    recipient varchar(200) not null,
    channel varchar(20) not null check (channel in ('IN_APP', 'EMAIL')),
    status varchar(20) not null default 'PENDING' check (status in ('PENDING', 'DELIVERED', 'EXPIRED')),
    attempts integer not null default 0 check (attempts >= 0),
    next_attempt_at timestamptz not null,
    expires_at timestamptz not null,
    delivered_at timestamptz,
    primary key (event_id, recipient, channel)
);
create index notification_outbox_due_idx on notification_outbox_deliveries(status, next_attempt_at);
create table notification_inbox (
    event_id varchar(200) not null references notification_outbox(event_id) on delete restrict,
    recipient varchar(200) not null,
    season_plan_id uuid not null references season_plans(id) on delete restrict,
    event_type varchar(80) not null,
    message text not null,
    occurred_at timestamptz not null,
    primary key (event_id, recipient)
);
insert into notification_outbox_deliveries (event_id, recipient, channel, next_attempt_at, expires_at)
select o.event_id, recipients.value, channels.value, o.occurred_at, o.occurred_at + interval '24 hours'
from notification_outbox o
cross join lateral jsonb_array_elements_text(o.recipients) recipients(value)
cross join lateral jsonb_array_elements_text(o.channels) channels(value)
on conflict do nothing;
