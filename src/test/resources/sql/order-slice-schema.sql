drop schema if exists orders cascade;
drop schema if exists outbox cascade;
drop schema if exists messaging cascade;

create schema orders;
create schema outbox;
create schema messaging;

create table orders.orders (
    id uuid primary key,
    customer_id uuid not null,
    status varchar(64) not null,
    currency varchar(8) not null,
    total_amount numeric(19, 2) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table orders.order_items (
    id uuid primary key,
    order_id uuid not null,
    sku varchar(64) not null,
    quantity int not null,
    unit_price numeric(19, 2) not null,
    line_total numeric(19, 2) not null,
    constraint fk_order_items_order
        foreign key (order_id) references orders.orders (id)
        on delete cascade
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
    dead_lettered_at timestamp with time zone,
    replay_count int not null default 0,
    replayed_at timestamp with time zone,
    replayed_by varchar(128)
);

create table messaging.processed_messages (
    consumer_name varchar(128) not null,
    message_id uuid not null,
    processed_at timestamp with time zone not null,
    constraint pk_messaging_processed_messages primary key (consumer_name, message_id)
);
