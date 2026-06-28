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
│   ├── PaymentService.java          # создание донатов и VIP, сохранение Payment
│   └── FxService.java               # конвертация валют в RUB по фикс. курсам
├── yookassa/
│   ├── YooKassaClient.java          # REST-клиент к api.yookassa.ru/v3
│   ├── CreatePaymentRequest.java
│   └── CreatePaymentResponse.java
├── repository/PaymentRepository.java # JPA + запросы топа донатеров
├── model/                            # Payment, PaymentType, PaymentStatus, Platform
├── util/NickValidator.java           # валидация/нормализация ника (regex, bed_ префикс)
└── config/                           # SiteConfig, SiteProperties, GlobalModelAttributes/Advice

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

### Webhook
`POST /yookassa/webhook?token=…` → `YooKassaWebhookController`:
- Проверка статического токена.
- Поиск платежа по `metadata.internal_payment_id`, fallback по `providerPaymentId`.
- Идемпотентность (если уже `SUCCEEDED` — выходим).
- ⚠️ Устанавливает статус **по телу запроса** (см. SEC-1).

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
| [SEC-1](../TODO.md) | 🔴 | Webhook доверяет телу запроса — нет проверки платежа в API ЮKassa |
| [SEC-2](../TODO.md) | 🔴 | Секреты (webhook-token, пароли БД, ИНН) в ресурсах |
| [SITE-3 / SEC-3](../TODO.md) | 🔴 | Нет фулфилмента: после оплаты VIP/донат не выдаётся в игру |
| [SITE-1](../TODO.md) | 🟠 | `DonateController` делает `redirect:` + `getConfirmationUrl()` — при null битый редирект/NPE; нет обработки ошибок ЮKassa |
| [SITE-4 / SEC-6](../TODO.md) | 🟠 | Нет CSRF-защиты и rate-limit на формах платежей |
| [SITE-2](../TODO.md) | 🟠 | `pay/return` показывает любой платёж по `pid` без привязки к пользователю (перебор UUID маловероятен, но статус виден) |
| [SITE-5](../TODO.md) | 🟡 | Курсы валют захардкожены, без автообновления |
| [SITE-6 / SEC-7](../TODO.md) | 🟡 | `spring-boot-devtools` не должен попадать в прод |
| [SITE-7](../TODO.md) | 🟡 | Нет тестов (только пустой `contextLoads`) |
| [SITE-8](../TODO.md) | 🟡 | `V2__platform.sql` пустая — упорядочить миграции/убрать заглушку |

## Как запустить

См. [../SETUP.md](../SETUP.md). Кратко: `cd server-site && ./mvnw spring-boot:run`
(нужен Postgres `server_site`), либо `docker compose up`.
