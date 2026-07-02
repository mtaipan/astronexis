# Модуль: server-site

Веб-сайт проекта на Spring Boot: лендинг, топ донатеров, юридические страницы и приём
платежей (донаты + VIP) через ЮKassa.

- **Стек:** Java 17+, Spring Boot 3.5.11, Thymeleaf, Spring Data JPA, Flyway, Lombok, PostgreSQL.
- **Сборка:** Maven (`./mvnw`). Docker + docker-compose присутствуют.
- **Порт:** 8080 (в docker-compose проброшен на 8083).

## Структура

```
server-site/src/main/java/dev/taipan/server_site/
├── ServerSiteApplication.java        # точка входа Spring Boot
├── controller/
│   ├── PageController.java           # GET /, /offer, /privacy, /contacts, /delivery, /pay/return
│   ├── DonateController.java         # POST /donate, POST /vip
│   └── YooKassaWebhookController.java# POST /yookassa/webhook
├── service/
│   ├── PaymentService.java              # создание донатов и VIP, сохранение Payment
│   ├── PaymentConfirmationService.java  # применяет статус (после проверки в API), кладёт грант в outbox
│   ├── FulfillmentService.java          # доставка VIP через RCON (SITE-9 закрыт: атомарный захват)
│   ├── VipGrantRetryScheduler.java      # дотягивает невыданные гранты (каждые 2 мин)
│   └── FxService.java                   # конвертация валют в RUB по фикс. курсам
├── yookassa/
│   ├── YooKassaClient.java          # REST-клиент к api.yookassa.ru/v3 (+ getPayment для верификации)
│   ├── CreatePaymentRequest.java
│   └── CreatePaymentResponse.java
├── rcon/{RconClient, RconException}.java # минимальный Source RCON поверх TCP
├── repository/                       # PaymentRepository (+ топ донатеров), VipGrantRepository
├── model/                            # Payment, PaymentType, PaymentStatus, Platform, VipGrant, GrantStatus
├── util/NickValidator.java           # валидация/нормализация ника (regex, bed_ префикс)
└── config/                           # SiteConfig, SiteProperties, RconProperties, GlobalModelAttributes/Advice

server-site/src/main/resources/
├── templates/                        # Thymeleaf: index, offer, privacy, contacts, delivery, pay_return
├── db/migration/                     # Flyway: V1__init.sql (payments), V2__platform.sql (no-op)
├── application.properties            # локальный профиль (СЕКРЕТЫ!)
├── application-docker.properties     # docker-профиль (СЕКРЕТЫ!)
└── application.yml                   # site.* + ПДн (ФИО, ИНН)
```

## Модель данных

Таблица `payments` (Flyway `V1__init.sql`):

| Поле | Тип | Назначение |
|---|---|---|
| `id` | uuid PK | внутренний id платежа (он же `metadata.internal_payment_id`) |
| `type` | varchar | `DONATION` \| `VIP_30D` |
| `platform` | varchar | `JAVA` \| `BEDROCK` |
| `nick` | varchar | игровой ник (нормализован) |
| `amount_original` / `currency_original` | numeric / varchar | сумма как ввёл пользователь |
| `amount_rub` | numeric | сумма в рублях (после `FxService`) |
| `status` | varchar | `PENDING` \| `SUCCEEDED` \| `CANCELED` |
| `provider` / `provider_payment_id` | varchar | `YOOKASSA` / id на стороне ЮKassa |
| `confirmation_url` | text | ссылка на оплату |
| `created_at` / `succeeded_at` | timestamptz | тайминги |

`V2__platform.sql` — пустая (no-op заглушка для совместимости истории миграций).

## Основные потоки

### Создание платежа
`POST /donate` или `POST /vip` → `PaymentService`:
1. `NickValidator.normalizeForPlatform` — проверка regex `^[A-Za-z0-9_]{3,16}$`, для Bedrock префикс `bed_`.
2. `FxService.toRub` — конвертация по фиксированным курсам (`fx.usd-to-rub` и т.д.); в ЮKassa всегда уходит RUB.
3. Сохранение `Payment(PENDING)`, вызов `YooKassaClient.createRedirectPayment` (Basic auth, `Idempotence-Key`).
4. Сохранение `providerPaymentId` + `confirmationUrl`, редирект пользователя на оплату.

### Webhook (SEC-1 закрыт)
`POST /yookassa/webhook?token=…` → `YooKassaWebhookController`:
- Статический токен — дешёвый фильтр от спама (не граница доверия).
- Из тела берётся **только `id`**; реальный статус/сумма/`metadata` читаются
  `GET /v3/payments/{id}` под Basic-credentials — **источник правды — ответ API**, а не тело.
- Сверка суммы, идемпотентность по статусу, 502 при сбое проверки (ЮKassa повторит).
- При `SUCCEEDED` для VIP кладёт грант в `vip_grants` (outbox) и пытается доставить сразу.

### Выдача VIP (SEC-3 и SITE-9 закрыты)
`PaymentConfirmationService` (короткая транзакция) создаёт durable-грант, `FulfillmentService`
доставляет его RCON-командой вне транзакции, `VipGrantRetryScheduler` дотягивает невыданное.
✅ **SITE-9 закрыт:** перед RCON-вызовом грант атомарно захватывается
(`claimForDelivery`: `UPDATE … SET status='DELIVERING' WHERE status='PENDING'`, проверка
affected rows) — webhook и планировщик не выдадут VIP дважды; зависшие DELIVERING
возвращаются в очередь через 10 мин. См. [SECURITY.md → SEC-10](../SECURITY.md).

### Витрина
`GET /` → топ донатеров: `topAllTime()` и `topSince(now-30d)` (сумма `amount_rub` по нику среди `DONATION/SUCCEEDED`).
`GET /pay/return?pid=…` → страница статуса платежа.

## 🟢 Сильные стороны

- Идемпотентность webhook и `Idempotence-Key` в клиенте ЮKassa.
- Чистая валидация/нормализация ника, единая логика для Java/Bedrock.
- Flyway-миграции, `open-in-view=false`, проекционные интерфейсы (`TopRow`) для агрегатов.
- Разделение профилей (local/docker), healthcheck в compose.

## 🔴🟠🟡 Слабые места / TODO

| ID | Severity | Проблема |
|---|---|---|
| [SITE-9 / SEC-10](../TODO.md) | 🟠✅ | **Гонка двойной выдачи закрыта:** атомарный захват + статус `DELIVERING` (`V4`); тест на два параллельных `deliver()` → 1 RCON (`FulfillmentServiceTest`) |
| [SEC-1](../TODO.md) | 🟢✅ | Webhook верифицирует платёж через API ЮKassa (из тела — только `id`) |
| [SEC-2](../TODO.md) | 🟢✅ | Секреты (webhook-token, пароли БД, ИНН) → ENV/плейсхолдеры |
| [SITE-3 / SEC-3](../TODO.md) | 🟢✅ | Фулфилмент реализован: outbox `vip_grants` + RCON + ретраи (RCON по умолчанию off) |
| [SITE-1](../TODO.md) | 🟢✅ | `redirect:null`/NPE устранён → `pay_error`; ошибки ЮKassa перехватываются |
| [SITE-4 / SEC-6](../TODO.md) | 🟠 | Нет CSRF-защиты и rate-limit на формах платежей |
| [SITE-2](../TODO.md) | 🟠 | `pay/return` показывает любой платёж по `pid` без привязки к пользователю (статус виден) |
| [SITE-5](../TODO.md) | 🟡 | Курсы валют захардкожены, без автообновления |
| [SITE-6 / SEC-7](../TODO.md) | 🟡 | `spring-boot-devtools` не должен попадать в прод |
| [SITE-7](../TODO.md) | 🟡 | Тесты появились точечно (`FulfillmentServiceTest`, 6 шт.); webhook/PaymentService/FxService не покрыты |
| [SITE-8](../TODO.md) | 🟡 | `V2__platform.sql` пустая — упорядочить миграции/убрать заглушку |
| [OPS-5](../TODO.md) | 🟡 | Мусорные файлы в каталоге (`=`, `CACHED`, `resolving`, `naming`, `exporting`, `unpacking`) |

## Как запустить

См. [../SETUP.md](../SETUP.md). Кратко: `cd server-site && ./mvnw spring-boot:run`
(нужен Postgres `server_site`), либо `docker compose up`.
