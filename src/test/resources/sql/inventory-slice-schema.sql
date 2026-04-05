drop schema if exists inventory cascade;
drop schema if exists outbox cascade;

create schema inventory;
create schema outbox;

create table inventory.inventory_items (
    sku varchar(64) primary key,
    available_quantity int not null,
    reserved_quantity int not null,
    updated_at timestamp with time zone not null
);

create table inventory.inventory_reservations (
    id uuid primary key,
    order_id uuid not null,
    sku varchar(64) not null,
    quantity int not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_inventory_reservation_order_sku unique (order_id, sku)
);

create table inventory.processed_messages (
    consumer_name varchar(128) not null,
    message_id uuid not null,
    processed_at timestamp with time zone not null,
    constraint pk_inventory_processed_messages primary key (consumer_name, message_id)
);

create table outbox.outbox_event (
    id uuid primary key,
    aggregate_id varchar(64) not null,
    aggregate_type varchar(64) not null,
    event_type varchar(128) not null,
    payload varchar(10000) not null,
    headers varchar(10000) not null,
    status varchar(32) not null,
    occurred_at timestamp with time zone not null,
    published_at timestamp with time zone,
    claimed_at timestamp with time zone,
    claimed_by varchar(128),
    retry_count int not null default 0,
    next_retry_at timestamp with time zone,
    last_error varchar(4000),
    last_failed_at timestamp with time zone,
    dead_lettered_at timestamp with time zone
);

