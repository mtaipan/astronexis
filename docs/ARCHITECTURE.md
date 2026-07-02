# Архитектура Astronexis

Документ описывает устройство каждого модуля и сквозные потоки данных. Подробности
по конкретному модулю — в [modules/](modules/). Известные дыры помечены ⚠ и ведут в
[SECURITY.md](SECURITY.md).

## 1. Поток авторизации игрока (TaipanAuthTg + AuthMe)

```
Игрок заходит ─▶ AuthMe требует /login или /register
                         │
                         ▼
        AuthMePoller (раз в секунду, В ТИК-ПОТОКЕ) опрашивает AuthMeApi.isAuthenticated()
                         │ переход false→true
                         ▼
              GateListener.onAuthMeLogin(player, true)
                         │ ⚠ SEC-11: здесь синхронный JDBC в главном потоке
        ┌────────────────┼─────────────────────────────┐
        ▼                ▼                               ▼
   bypass-право?    trust-сессия валидна?         есть привязка ник↔chat_id?
   да → пускаем     да → пускаем                  нет → блок «привяжи TG»
                                                  да  → генерим код6, шлём в TG
                                                         │
                                                         ▼
                             Игрок вводит /tgcode <код>  (GateListener.tryAcceptCode)
                                                         │ код верный
                                                         ▼
                             создаём trust-сессию (fingerprint+TTL), размораживаем
```

Пока игрок не авторизован, `GateListener` держит его «замороженным»: отменяет перемещение,
урон, голод, возгорание, чат, интеракции, блоки, дроп/инвентарь и все команды кроме
`/login /register /auth /tgcode`.

**⚠ Две проблемы в этом потоке:**
- **SEC-8 (обход 2FA):** заморозка опирается на наличие записи `pending`. Когда TTL кода
  истекает, `isBlocked()` удаляет запись и возвращает `false` → игрок считается авторизованным
  **без ввода кода**. Достаточно переждать 120с. Это дыра в сердце модуля.
- **SEC-11 (JDBC в тике):** опрос AuthMe идёт через `scheduleSyncRepeatingTask` (главный поток),
  и `onAuthMeLogin` синхронно ходит в Postgres. Деградация БД → лаги/фриз сервера.

Опрос поллингом (а не по событиям AuthMe) даёт задержку до ~1с и постоянную нагрузку — см.
`AUTH-3`. Детали — в [modules/TaipanAuthTg.md](modules/TaipanAuthTg.md).

## 2. Поток привязки и доступа (TgBot)

```
Пользователь пишет боту ─▶ @require_access (декоратор-guard на хендлере)
                                 │
              ┌──────────────────┼───────────────────┐
              ▼                  ▼                    ▼
        AccessService.check:  забанен? → отказ   подписан на каналы? → если нет, отказ
              │                                   (+ опц. удаление привязки)
              │  ⚠ SEC-13: пустой список каналов / сбой проверки → доступ РАЗРЕШЁН (fail-open)
              ▼
        upsert профиля в users (для поиска админом)
              │
              ▼
        Хендлер команды (start / bind / support / admin / ...)
```

- **bind**: пошаговый диалог (выбор платформы → ввод ника), запись в `bindings` через
  `BindingsService.bind_strict` со строгими правилами уникальности.
- **support/tickets**: тикеты и сообщения в таблицах `tickets` / `ticket_messages`,
  уведомления админам через `NotifyService`.
- **admin**: команды модерации — **⚠ BOT-8/SEC-9:** большинство из них объявлены в `admin.py`,
  но **не зарегистрированы** в `main.py`, поэтому не работают (бан, каналы, привязки, поиск).

Доступ проверяется единообразно через декоратор `@require_access` (см. [ADR-5](DECISIONS.md)) —
забыть проверку в отдельном хендлере уже нельзя. Конфигурация бота — **только из ENV**
(`app/core/config.py`); прежний `config.yaml` удалён, один источник правды.

## 3. Поток платежей (server-site + ЮKassa)

```
Форма на сайте (/donate или /vip)
        │  POST nick, amount, currency
        ▼
DonateController ─▶ PaymentService.createDonation/createVip30
        │             │ нормализует ник, конвертирует в RUB (FxService), валидирует сумму
        │             │ сохраняет Payment(status=PENDING)
        │             ▼
        │        YooKassaClient.createRedirectPayment  ──▶ POST api.yookassa.ru/v3/payments
        │             │  (Basic auth shopId:secretKey, Idempotence-Key, таймауты)
        │             ▼
        │        сохраняет providerPaymentId + confirmationUrl
        ▼
redirect:confirmationUrl ─▶ пользователь платит на стороне ЮKassa
        │
        ▼
ЮKassa ─▶ POST /yookassa/webhook?token=...
        │
        ▼
YooKassaWebhookController:
   проверяет статический token (query) — дешёвый фильтр от спама
   берёт из тела ТОЛЬКО id платежа
   GET /v3/payments/{id} под своими credentials  ← источник правды (не тело!)
   сверяет сумму, идемпотентность по статусу
        │
        ▼
PaymentConfirmationService.confirm (короткая транзакция БД):
   status=SUCCEEDED → для VIP кладёт грант в vip_grants (outbox, идемпотентно по payment_id)
        │
        ▼
FulfillmentService.tryDeliver (ВНЕ транзакции):
   RCON-команда на сервер  ⚠ SEC-10: без блокировки строки → возможна двойная выдача
        │
        ▼
VipGrantRetryScheduler (каждые 2 мин): дотягивает невыданные гранты
```

```
Возврат игрока:  GET /pay/return?pid=... ─▶ PageController ─▶ показывает статус платежа
Главная:         GET / ─▶ топ донатеров (topAllTime / topSince) из успешных платежей
```

Webhook теперь безопасен относительно подделки тела (SEC-1 закрыт, [ADR-1](DECISIONS.md)),
но остаётся **гонка двойной доставки** (SEC-10): webhook и планировщик вызывают `deliver()`
без блокировки строки. См. [ADR-2](DECISIONS.md).

## 4. Архитектура игрового ядра (AstronexisCore)

Модульный «монолит-плагин»:

```
AstronexisCorePlugin.onEnable()
   ├─ ServiceRegistry.init()        // общие сервисы
   │     ├─ MessageService          // i18n из конфигов
   │     └─ PlatformService         // определение Java/Bedrock (Floodgate)
   ├─ ModuleManager
   │     └─ register(MenuModule) если modules.menu=true
   └─ ModuleManager.enableAll()
        └─ MenuModule.enable()
              ├─ LastLocationStore   // запоминание последней локации
              ├─ MenuService         // логика страниц меню
              ├─ ActionBarService    // периодический action bar
              ├─ MenuListener / CompassListener / CompassProtectListener
              ├─ HubCompassGiver     // выдача компаса-навигатора
              └─ MenuCommand (/menu)
```

Рендер меню разделён по платформам: `JavaMenuRenderer` (инвентарь-сундук) и
`BedrockMenuRenderer` (Floodgate-формы). Паттерн `CoreModule { id, enable, disable }`
позволяет включать/выключать функциональность флагами в `config.yml`. Это самая зрелая
часть проекта.

## 5. Хранилища данных

| Хранилище | Кто использует | Таблицы/файлы |
|---|---|---|
| Postgres (БД бота) | TgBot, **TaipanAuthTg** | `users`, `bindings`, `bans`, `required_channels`, `settings`, `tickets`, `ticket_messages` |
| Postgres (БД сайта) | server-site | `payments`, `vip_grants` |
| YAML (fallback) | TaipanAuthTg | `bindings.yml`, `trust` store (переключается `storage.type`) |

Схема БД бота управляется **Alembic** (`TgBot/alembic/versions`), схема сайта — **Flyway**
(`server-site/.../db/migration`). У `TaipanAuthTg` обе реализации store (`Pg*` и `Yaml*`)
переключаются параметром `storage.type` в `config.yml`.

> ⚠️ **ARCH-4 (главный архитектурный долг).** `TaipanAuthTg` читает таблицу `bindings` **сырым
> SQL**, но схему этой таблицы определяют миграции **TgBot** — две кодовые базы делят таблицу
> без контракта. Изменение схемы ботом молча ломает плагин. А `vip_grants` лежит в **БД сайта**,
> тогда как «плагин-консьюмер», который её должен читать, ходит в **БД бота** и вообще не
> написан. Итог: 4 модуля, 2 базы, связи через прямой доступ к чужим таблицам и опциональный
> RCON. Нужен явный контракт (внутренний API или брокер) — см. `ARCH-4` в [TODO.md](TODO.md).

## 6. Сквозные архитектурные риски

- **⚠ SEC-8 (обход 2FA)** — истечение TTL кода размораживает игрока. `AUTH-7`. Критично.
- **⚠ SEC-9 (мёртвая админка)** — команды модерации не зарегистрированы. `BOT-8`. Критично.
- **⚠ SEC-10 (двойная выдача VIP)** — нет блокировки строки при доставке. `SITE-9`.
- **⚠ SEC-11 (JDBC в тике)** — БД-вызовы в главном потоке сервера. `AUTH-8`.
- **ARCH-4** — интеграция через прямой доступ к чужим таблицам, без контракта.
- ~~Два источника конфигурации бота~~ — `config.yaml` удалён, источник один (ENV). `BOT-1` ✅.
- ~~Дублирование меню~~ — `TaipanMenuPlugin` удалён, всё в `AstronexisCore`. `ARCH-2` ✅.
- **Секреты** — вынесены в ENV/плейсхолдеры (`SEC-2`); история git чистая; осталось отозвать
  токен/сменить пароли (ручное).
- **Мусор в репо** — закоммичены пустышки (`server-site/=`, `CACHED`, … , пустой `schema.sql`). `OPS-5`.
