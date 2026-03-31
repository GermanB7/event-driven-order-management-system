create schema if not exists orders;
create schema if not exists inventory;
create schema if not exists payments;
create schema if not exists shipping;
create schema if not exists workflow;
create schema if not exists outbox;

create table if not exists outbox.outbox_event (
    id uuid primary key,
    aggregate_id varchar(64) not null,
    aggregate_type varchar(64) not null,
    event_type varchar(128) not null,
    payload jsonb not null,
    headers jsonb not null,
    status varchar(32) not null,
    occurred_at timestamptz not null,
    published_at timestamptz,
    retry_count int not null default 0,
    next_retry_at timestamptz
);

create index if not exists idx_outbox_event_status_occurred_at
    on outbox.outbox_event (status, occurred_at);

