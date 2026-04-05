alter table outbox.outbox_event
    add column if not exists replay_count int not null default 0,
    add column if not exists replayed_at timestamptz,
    add column if not exists replayed_by varchar(128);

create index if not exists idx_outbox_event_replayed_at
    on outbox.outbox_event (replayed_at)
    where replayed_at is not null;

