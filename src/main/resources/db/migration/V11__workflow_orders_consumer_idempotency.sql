create schema if not exists messaging;

create table if not exists messaging.processed_messages (
    consumer_name varchar(128) not null,
    message_id uuid not null,
    processed_at timestamp with time zone not null,
    constraint pk_messaging_processed_messages primary key (consumer_name, message_id)
);

create index if not exists idx_messaging_processed_messages_processed_at
    on messaging.processed_messages (processed_at);

