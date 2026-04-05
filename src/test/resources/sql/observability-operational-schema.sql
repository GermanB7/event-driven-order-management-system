drop schema if exists inventory cascade;
drop schema if exists payments cascade;
drop schema if exists shipping cascade;

create schema inventory;
create schema payments;
create schema shipping;

create table inventory.inventory_reservations (
    id uuid primary key,
    order_id uuid not null,
    sku varchar(64) not null,
    quantity int not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_inventory_reservations_order_id
    on inventory.inventory_reservations (order_id);

create table payments.payments (
    id uuid primary key,
    order_id uuid not null,
    amount numeric(19, 2) not null,
    currency varchar(8) not null,
    status varchar(32) not null,
    failure_reason varchar(512),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_payments_order_id on payments.payments (order_id);

create table shipping.shipments (
    id uuid primary key,
    order_id uuid not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_shipments_order_id on shipping.shipments (order_id);

