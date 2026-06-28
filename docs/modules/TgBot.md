# Модуль: TgBot

Telegram-бот проекта: привязка игрового ника к Telegram, проверка подписки на каналы,
тикеты поддержки, админ-панель.

- **Стек:** Python 3.12, python-telegram-bot 21.6, SQLAlchemy 2.0 (async + asyncpg),
  Alembic, python-dotenv.
- **Запуск:** long polling (`run_polling`).
- **Хранилище:** PostgreSQL (async). Есть также YAML-сторы (`storage/*_yaml.py`) — legacy.

## Структура

```
TgBot/
├── alembic.ini, alembic/versions/   # миграции 0001..0004
├── config.yaml                      # ⚠️ ВТОРОЙ источник конфигурации (+ реальный токен)
├── .env                             # переменные окружения (секреты)
├── deploy/                          # Dockerfile, docker-compose, entrypoint
└── src/app/
    ├── main.py                      # сборка Application, регистрация хендлеров
    ├── core/config.py               # load_config() из ENV → AppConfig
    ├── db/
    │   ├── engine.py                # create_async_engine
    │   ├── models.py                # ORM: User, Binding, Ban, RequiredChannel, Setting, Ticket, TicketMessage
    │   └── repo/                    # репозитории (users, bindings, bans, channels, settings, tickets, ticket_messages)
    ├── services/
    │   ├── access_service.py        # бан + подписки + upsert профиля
    │   ├── subscriptions_service.py # проверка членства в каналах
    │   ├── bindings_service.py      # строгие правила привязки ника
    │   ├── tickets_service.py       # создание тикетов
    │   └── notify_service.py        # уведомления админам с тумблерами
    ├── bot/
    │   ├── middlewares/guard.py     # access-check + upsert профиля перед хендлером
    │   ├── handlers/                # start, menu, bind, support, support_text, tickets_admin, admin, admin_notify
    │   └── texts/ru.py              # тексты интерфейса
    └── storage/                     # bans_yaml, bindings_yaml, state_yaml (legacy)
```

## Модель данных (ORM, `db/models.py`)

- **User** (`users`): `tg_id` PK, `tg_username/first/last`, `created_at`, `updated_at`.
- **Binding** (`bindings`): PK `(platform, nick)`, FK `tg_id`. Правила уникальности:
  один TG ↔ один ник на платформу; один ник ↔ один TG. Bedrock-ник хранится как `bed_<nick>`.
- **Ban** (`bans`): `tg_id` PK, `reason`.
- **RequiredChannel** (`required_channels`): список каналов для обязательной подписки.
- **Setting** (`settings`): key→value (`enforce_subscriptions`, `delete_binding_on_unsub`,
  `nick_change_price`, `notify_support`, `notify_mail`, …).
- **Ticket** (`tickets`) / **TicketMessage** (`ticket_messages`): поддержка.

Миграции Alembic: `0001_init`, `0002_bind_tickets_price`, `0003_bind_platform_credits`,
`0004_user_profile_search`.

## Ключевые потоки

### guard (middleware)
[bot/middlewares/guard.py](../../TgBot/src/app/bot/middlewares/guard.py): на каждый апдейт —
`AccessService.check` (бан? подписки?) + `_upsert_user_profile` (fail-safe, молча пропускает,
если колонок ещё нет). Возвращает `True/False`, хендлеры сами вызывают guard.

### Привязка (`bind`)
Пошаговый диалог (платформа → ник). `BindingsService.bind_strict` запрещает обычному
пользователю перезаписывать чужой ник и менять свой (смена — через оплату/саппорт).

### Доступ (`access_service`)
Бан → отказ. Если `enforce_subscriptions=true` и есть каналы — проверка подписки
(`subscriptions_service`). При отписке опционально удаляет привязку (`delete_binding_on_unsub`).

### Тикеты
`support` создаёт тикет + первое сообщение; `tickets_admin` (`/tickets /reply /close`) —
обработка админом; `notify_service` шлёт уведомления с тумблерами из `settings`.

## 🟢 Сильные стороны

- Чистая слоёная архитектура: `handlers → services → repo → db`.
- **Параметризованный SQL** (`text(...)` с `:params`) — SQL-инъекций не обнаружено.
- Fail-safe подходы (upsert профиля, рассылка админам не падает из-за одного получателя).
- Async-стек, `pool_pre_ping`, миграции Alembic, тексты вынесены отдельно.

## 🔴🟠🟡 Слабые места / TODO

| ID | Severity | Проблема |
|---|---|---|
| [BOT-2](../TODO.md) | 🔴 | **Два `MessageHandler` на один фильтр** `TEXT & ~COMMAND` (`bind_on_text` и `support_on_text`) в одной группе — PTB выполнит только первый, `support_on_text` никогда не сработает. Нужны разные группы или единый роутер. |
| [SEC-2](../TODO.md) | 🔴 | Реальный bot-token и креды в `config.yaml`/`.env` |
| [BOT-1](../TODO.md) | 🟠 | Два источника конфигурации: `core/config.py` (ENV) и `config.yaml` — расходятся. Выбрать один. |
| [ARCH-1](../TODO.md) | 🟠 | Источник правды для `bindings` пересекается с TaipanAuthTg (Postgres vs YAML-путь в `config.yaml`) |
| [BOT-3](../TODO.md) | 🟠 | `guard` вызывается вручную в каждом хендлере — легко забыть и оставить эндпоинт без проверки |
| [BOT-4](../TODO.md) | 🟡 | Legacy YAML-сторы (`storage/*_yaml.py`) сосуществуют с Postgres — удалить или задокументировать |
| [BOT-5](../TODO.md) | 🟡 | `nickchange`-хендлер закомментирован в `main.py` — доделать или убрать |
| [BOT-6](../TODO.md) | 🟡 | Нет тестов, нет обработки сетевых ошибок Telegram в части хендлеров |
| [BOT-7](../TODO.md) | 🟡 | `admin.py` ~400 строк — разбить по областям (channels/bans/bindings/tickets/price) |

## Как запустить

См. [../SETUP.md](../SETUP.md). Нужны `BOT_TOKEN`, `DATABASE_URL`, `ADMINS` в окружении,
`alembic upgrade head`, затем `python -m app.main` из `TgBot/src`.
