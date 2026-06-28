# Архитектура Astronexis

Документ описывает устройство каждого модуля и сквозные потоки данных. Подробности
по конкретному модулю — в [modules/](modules/).

## 1. Поток авторизации игрока (TaipanAuthTg + AuthMe)

```
Игрок заходит ─▶ AuthMe требует /login или /register
                         │
                         ▼
        AuthMePoller (каждую секунду) опрашивает AuthMeApi.isAuthenticated()
                         │ переход false→true
                         ▼
              GateListener.onAuthMeLogin(player, true)
                         │
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

Пока игрок не авторизован, `GateListener` держит его «замороженным»: отменяет
перемещение, урон, голод, возгорание и все команды кроме `/login /register /auth /tgcode`.

**Важно:** опрос AuthMe идёт поллингом раз в секунду (`scheduleSyncRepeatingTask`), а не
по событию — это упрощает интеграцию, но даёт задержку до ~1с и постоянную нагрузку.
Подробнее и о пробелах заморозки — в [modules/TaipanAuthTg.md](modules/TaipanAuthTg.md).

## 2. Поток привязки и доступа (TgBot)

```
Пользователь пишет боту ─▶ guard middleware
                                 │
              ┌──────────────────┼───────────────────┐
              ▼                  ▼                    ▼
        AccessService.check:  забанен? → отказ   подписан на каналы? → если нет, отказ
              │                                   (+ опц. удаление привязки)
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
- **admin**: управление каналами, банами, привязками, ценой смены ника, тикетами.

Конфигурация бота читается из переменных окружения (`app/core/config.py`), но
**параллельно существует `config.yaml`** со своими значениями — два источника правды
(см. `BOT-1` в [TODO.md](TODO.md)).

## 3. Поток платежей (server-site + ЮKassa)

```
Форма на сайте (/donate или /vip)
        │  POST nick, amount, currency
        ▼
DonateController ─▶ PaymentService.createDonation/createVip30
        │             │ нормализует ник, конвертирует в RUB (FxService)
        │             │ сохраняет Payment(status=PENDING)
        │             ▼
        │        YooKassaClient.createRedirectPayment  ──▶ POST api.yookassa.ru/v3/payments
        │             │  (Basic auth shopId:secretKey, Idempotence-Key)
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
   проверяет статический token (query)
   находит платёж по metadata.internal_payment_id (fallback: providerPaymentId)
   ⚠ доверяет полю status из ТЕЛА запроса → ставит SUCCEEDED/CANCELED
        │
        ▼
   (фулфилмента в игру НЕТ)
```

```
Возврат игрока:  GET /pay/return?pid=... ─▶ PageController ─▶ показывает статус платежа
Главная:         GET / ─▶ топ донатеров (topAllTime / topSince) из успешных платежей
```

Критическая проблема — webhook доверяет телу запроса вместо обратной проверки в ЮKassa.
См. [SECURITY.md](SECURITY.md), задача `SEC-1`.

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
позволяет включать/выключать функциональность флагами в `config.yml`.

## 5. Хранилища данных

| Хранилище | Кто использует | Таблицы/файлы |
|---|---|---|
| Postgres (бот) | TgBot, TaipanAuthTg | `users`, `bindings`, `bans`, `required_channels`, `settings`, `tickets`, `ticket_messages` |
| Postgres (сайт) | server-site | `payments` |
| YAML (legacy/fallback) | TaipanAuthTg, TgBot | `bindings.yml`, `trust` store, `tg_state.yml`, `tg_bans.yml` |

Схема бота управляется **Alembic** (`TgBot/alembic/versions`), схема сайта — **Flyway**
(`server-site/.../db/migration`). У `TaipanAuthTg` обе реализации store (`Pg*` и `Yaml*`)
переключаются параметром `storage.type` в `config.yml`.

> ⚠️ Базы данных бота и плагина авторизации логически пересекаются (таблица `bindings`),
> но конфиги указывают на разные подключения/файлы. Нужно явно зафиксировать единый
> источник правды (см. `ARCH-1` в [TODO.md](TODO.md)).

## 6. Сквозные архитектурные риски

- ~~Два источника конфигурации бота~~ — мёртвый `config.yaml` удалён, источник один (env). `BOT-1`.
- ~~Дублирование меню~~ — `TaipanMenuPlugin` удалён, всё в `AstronexisCore`. `ARCH-2` ✅.
- **Секреты** — вынесены в `.env`/ENV и плейсхолдеры (`SEC-2`); осталось отозвать токен/сменить пароли.
- **Нет автоматической доставки покупок в игру** — `SITE-3`.
- **Связь сайт↔игра отсутствует**: платежи знают ник, но никак не воздействуют на сервер.
