alter table outbox.outbox_event
    add constraint chk_outbox_event_status
        check (status in ('PENDING', 'PUBLISHED', 'FAILED'));

create index if not exists idx_outbox_event_status_next_retry_occurred_at
    on outbox.outbox_event (status, next_retry_at, occurred_at);

