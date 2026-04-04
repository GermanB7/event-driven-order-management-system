create table if not exists inventory.processed_messages (
    consumer_name varchar(128) not null,
    message_id uuid not null,
    processed_at timestamptz not null,
    constraint pk_inventory_processed_messages primary key (consumer_name, message_id)
);

create index if not exists idx_inventory_processed_messages_processed_at
    on inventory.processed_messages (processed_at);


