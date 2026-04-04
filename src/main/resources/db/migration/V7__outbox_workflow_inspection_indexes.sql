create index if not exists idx_outbox_event_aggregate_occurred_id
    on outbox.outbox_event (aggregate_id, occurred_at, id);

create index if not exists idx_outbox_event_status_next_retry
    on outbox.outbox_event (status, next_retry_at desc nulls last)
    where status = 'FAILED';

create index if not exists idx_outbox_event_headers_workflow_id
    on outbox.outbox_event using GIN ((headers -> 'workflowId'))
    where headers ? 'workflowId';


