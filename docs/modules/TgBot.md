# Модуль: TgBot

Telegram-бот проекта: привязка игрового ника к Telegram, проверка подписки на каналы,
тикеты поддержки, админ-панель.

- **Стек:** Python 3.12, python-telegram-bot 21.6, SQLAlchemy 2.0 (async + asyncpg),
  Alembic, python-dotenv.
- **Запуск:** long polling (`run_polling`).
- **Хранилище:** PostgreSQL (async). Legacy YAML-сторы удалены — единственное хранилище Postgres.

## Структура

```
TgBot/
├── alembic.ini, alembic/versions/   # миграции 0001..0004 (источник схемы БД)
├── .env                             # переменные окружения (секреты; в репо — плейсхолдеры)
├── deploy/                          # Dockerfile, docker-compose, entrypoint
└── src/app/
    ├── main.py                      # сборка Application, регистрация хендлеров
    │                                #   BOT-8 закрыт: админ-команды списком ADMIN_COMMANDS
    ├── schema.sql                   # ⚠ пустой (0 байт), вводит в заблуждение — удалить (OPS-5)
    ├── core/config.py               # load_config() из ENV → AppConfig (единственный источник)
    ├── db/
    │   ├── engine.py                # create_async_engine
    │   ├── models.py                # ORM: User, Binding, Ban, RequiredChannel, Setting, Ticket, TicketMessage
    │   └── repo/                    # репозитории (users, bindings, bans, channels, settings, tickets, ticket_messages)
    ├── services/
    │   ├── access_service.py        # бан + подписки + upsert профиля (⚠ SEC-13: fail-open)
    │   ├── subscriptions_service.py # проверка членства в каналах
    │   ├── bindings_service.py      # строгие правила привязки ника
    │   ├── tickets_service.py       # создание тикетов
    │   └── notify_service.py        # уведомления админам с тумблерами
    └── bot/
        ├── middlewares/guard.py     # @require_access: access-check + upsert профиля перед хендлером
        ├── handlers/                # start, menu, bind, support, support_text, text_router, tickets_admin, admin, admin_notify
        └── texts/ru.py              # тексты интерфейса
```

> **Устарело в прошлой версии:** `config.yaml` (второй источник конфига) и пакет `storage/*_yaml.py`
> (legacy YAML-сторы) **удалены** — их больше нет. Конфигурация читается только из ENV.

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

### guard (декоратор `@require_access`)
[bot/middlewares/guard.py](../../TgBot/src/app/bot/middlewares/guard.py): декоратор оборачивает
хендлер — сначала `AccessService.check` (бан? подписки?) + `_upsert_user_profile` (fail-safe),
и только при `True` вызывается сам хендлер. Забыть проверку в отдельном хендлере уже нельзя
(было BOT-3, см. [ADR-5](../DECISIONS.md)). ⚠️ `AccessService` fail-open: пустой список каналов
или сбой проверки подписки → доступ разрешается (SEC-13 / BOT-9).

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
| [BOT-8 / SEC-9](../TODO.md) | 🔴✅ | **Админка подключена целиком:** 15 команд регистрируются списком `ADMIN_COMMANDS` циклом; smoke-тест `tests/test_admin_registration.py` сверяет меню `/admin` с реально зарегистрированными |
| [BOT-9 / SEC-13](../TODO.md) | 🟡✅ | Политика зафиксирована и поправлена: пустой список каналов → доступ (осознанно); сбой Telegram API → отказ, но привязка **не** удаляется (`is_subscribed` различает `False`/`None`). Тесты: `tests/test_access_service.py` |
| [BOT-2](../TODO.md) | 🟢✅ | Конфликт двух `MessageHandler` устранён: единый `text_router` разводит по `step` |
| [SEC-2](../TODO.md) | 🟢✅ | Токен/креды вынесены из репо (в `.env` — плейсхолдер); ручное: перевыпустить токен |
| [BOT-1](../TODO.md) | 🟢✅ | Один источник конфигурации — ENV; `config.yaml` удалён |
| [ARCH-1 / ARCH-4](../TODO.md) | 🟠 | Таблицу `bindings` (домен бота) читает плагин авторизации сырым SQL — нет контракта |
| [BOT-3](../TODO.md) | 🟢✅ | `@require_access` на всех хендлерах — проверку нельзя забыть ([ADR-5](../DECISIONS.md)) |
| [BOT-4](../TODO.md) | 🟢✅ | Legacy YAML-сторы удалены |
| [BOT-5](../TODO.md) | 🟢✅ | Закомментированный `nickchange` убран из `main.py` |
| [BOT-6](../TODO.md) | 🟡 | Тесты появились (`tests/`: регистрация команд, политика доступа — 8 шт.); обработка сетевых ошибок Telegram в части хендлеров всё ещё не везде |
| [BOT-7](../TODO.md) | 🟡 | `admin.py` ~380 строк — разбить по областям (channels/bans/bindings/tickets/price) |

## Как запустить

См. [../SETUP.md](../SETUP.md). Нужны `BOT_TOKEN`, `DATABASE_URL`, `ADMINS` в окружении,
`alembic upgrade head`, затем `python -m app.main` из `TgBot/src`.
