alter table outbox.outbox_event
    add column if not exists claimed_at timestamptz,
    add column if not exists claimed_by varchar(128),
    add column if not exists last_error text,
    add column if not exists last_failed_at timestamptz,
    add column if not exists dead_lettered_at timestamptz;

alter table outbox.outbox_event
    drop constraint if exists chk_outbox_event_status;

alter table outbox.outbox_event
    add constraint chk_outbox_event_status
        check (status in ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED', 'DEAD_LETTER'));

create index if not exists idx_outbox_event_relay_ready
    on outbox.outbox_event (status, next_retry_at, occurred_at)
    where status in ('PENDING', 'FAILED');

create index if not exists idx_outbox_event_claimed_at
    on outbox.outbox_event (status, claimed_at)
    where status = 'IN_PROGRESS';

