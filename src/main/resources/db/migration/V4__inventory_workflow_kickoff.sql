create table if not exists inventory.inventory_items (
    sku varchar(64) primary key,
    available_quantity int not null check (available_quantity >= 0),
    reserved_quantity int not null check (reserved_quantity >= 0),
    updated_at timestamptz not null
);

create table if not exists inventory.inventory_reservations (
    id uuid primary key,
    order_id uuid not null,
    sku varchar(64) not null,
    quantity int not null check (quantity > 0),
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_inventory_reservation_item
        foreign key (sku) references inventory.inventory_items (sku),
    constraint uk_inventory_reservation_order_sku unique (order_id, sku)
);

create index if not exists idx_inventory_reservations_order_id on inventory.inventory_reservations (order_id);
create index if not exists idx_inventory_reservations_status on inventory.inventory_reservations (status);

