create table if not exists orders.orders (
    id uuid primary key,
    customer_id uuid not null,
    status varchar(64) not null,
    currency varchar(8) not null,
    total_amount numeric(19, 2) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists orders.order_items (
    id uuid primary key,
    order_id uuid not null,
    sku varchar(64) not null,
    quantity int not null check (quantity > 0),
    unit_price numeric(19, 2) not null check (unit_price > 0),
    line_total numeric(19, 2) not null,
    constraint fk_order_items_order
        foreign key (order_id) references orders.orders (id)
        on delete cascade
);

create index if not exists idx_orders_created_at on orders.orders (created_at);
create index if not exists idx_orders_status on orders.orders (status);
create index if not exists idx_order_items_order_id on orders.order_items (order_id);

