-- SITE-9: защита от двойной выдачи VIP.
-- Доставку теперь предваряет атомарный захват гранта (status PENDING -> DELIVERING),
-- чтобы webhook и планировщик ретраев не отправили RCON-команду по одному гранту дважды.
-- claimed_at нужен, чтобы возвращать в очередь гранты, зависшие в DELIVERING
-- после падения процесса между захватом и результатом доставки.
alter table vip_grants add column if not exists claimed_at timestamptz;

-- status теперь: PENDING | DELIVERING | DELIVERED | FAILED
