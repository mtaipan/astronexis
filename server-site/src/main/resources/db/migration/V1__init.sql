create table if not exists payments (
    id uuid primary key,
    type varchar(32) not null,
    platform varchar(16) not null,
    nick varchar(64) not null,
    title varchar(256) not null,

    amount_original numeric(12,2) not null,
    currency_original varchar(8) not null,
    amount_rub numeric(12,2) not null,

    status varchar(16) not null,

    provider varchar(32) not null,
    provider_payment_id varchar(128),
    confirmation_url text,

    created_at timestamptz not null,
    succeeded_at timestamptz
);

create index if not exists idx_payments_provider_pid on payments(provider, provider_payment_id);
create index if not exists idx_payments_status_type on payments(status, type);
create index if not exists idx_payments_succeeded_at on payments(succeeded_at);
