create table if not exists shipping.shipments (
    id uuid primary key,
    order_id uuid not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_shipments_order_id unique (order_id)
);

create index if not exists idx_shipments_status on shipping.shipments (status);
create index if not exists idx_shipments_created_at on shipping.shipments (created_at);

