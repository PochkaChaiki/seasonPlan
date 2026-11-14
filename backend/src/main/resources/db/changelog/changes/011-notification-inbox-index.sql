--liquibase formatted sql

--changeset seasonplan:011-notification-inbox-index
create index notification_inbox_recipient_newest_idx on notification_inbox(recipient, occurred_at desc, event_id desc);
