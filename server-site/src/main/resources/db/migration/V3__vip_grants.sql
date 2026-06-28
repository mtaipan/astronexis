-- Очередь выдачи VIP (transactional outbox).
-- Запись создаётся в той же транзакции, что и перевод платежа в SUCCEEDED.
-- Доставка (RCON-команда на игровой сервер) идёт асинхронно с ретраями,
-- поэтому грант переживает оффлайн сервера/сбой RCON. Также таблицу может
-- читать игровой плагин (выдача при следующем входе игрока).
create table if not exists vip_grants (
    id              uuid primary key,
    payment_id      uuid not null references payments(id),
    nick            varchar(64) not null,
    platform        varchar(16) not null,
    duration_days   int  not null,
    status          varchar(16) not null,         -- PENDING | DELIVERED | FAILED
    attempts        int  not null default 0,
    last_error      text,
    created_at      timestamptz not null,
    delivered_at    timestamptz
);

-- Один грант на платёж (идемпотентность выдачи).
create unique index if not exists uq_vip_grants_payment on vip_grants(payment_id);
create index if not exists idx_vip_grants_status on vip_grants(status);
