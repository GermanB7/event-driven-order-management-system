create table if not exists payments.payments (
    id uuid primary key,
    order_id uuid not null,
    amount numeric(19, 2) not null check (amount > 0),
    currency varchar(8) not null,
    status varchar(32) not null,
    failure_reason varchar(512),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_payments_order_id unique (order_id)
);

create index if not exists idx_payments_status on payments.payments (status);
create index if not exists idx_payments_created_at on payments.payments (created_at);

