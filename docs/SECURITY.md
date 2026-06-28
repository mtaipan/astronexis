# Безопасность Astronexis

Список найденных уязвимостей и рисков с оценкой и привязкой к задачам в [TODO.md](TODO.md).
Отсортировано по серьёзности.

---

## 🔴 SEC-1. Подделка платежей через webhook ЮKassa

**Где:** [server-site/.../controller/YooKassaWebhookController.java](../server-site/src/main/java/dev/taipan/server_site/controller/YooKassaWebhookController.java)

**Суть:** статус платежа `SUCCEEDED`/`CANCELED` выставляется по полю `status` / `event`
**из тела HTTP-запроса**, без обратного обращения к API ЮKassa за подтверждением.
Единственная защита — статический `token` в query-параметре.

**Эксплуатация:** атакующий, знающий URL и токен (а токен сейчас захардкожен в
`application.properties` и `application-docker.properties`, см. SEC-2), может отправить
поддельный POST и пометить любой `PENDING`-платёж как оплаченный.

**Почему токена недостаточно:** ЮKassa не подписывает уведомления простым query-токеном.
Правильная схема — одна из:
1. На каждое уведомление делать `GET https://api.yookassa.ru/v3/payments/{id}` под своими
   Basic-credentials и доверять **только** ответу API (рекомендуется).
2. Дополнительно ограничить источник по официальным IP-диапазонам ЮKassa.

**Фикс:** `SEC-1` в TODO.

---

## 🔴 SEC-2. Реальные секреты в репозитории

> **Статус:** частично исправлено (см. [TODO.md](TODO.md) → SEC-2). Живые секреты вынесены
> в плейсхолдеры/ENV, добавлены `.env.example` и `.gitignore`, мёртвый `config.yaml` удалён.
> **Остаётся за тобой:** отозвать старый bot-token у @BotFather и сменить пароли БД.

**Где было:**
- `TgBot/config.yaml` — **живой Telegram bot-token**, chat_id админа, абсолютные пути
  (файл удалён, он не читался кодом).
- [TgBot/.env](../TgBot/.env) — `BOT_TOKEN`, `DATABASE_URL`, `ADMINS`, хосты.
- [server-site/.../application.properties](../server-site/src/main/resources/application.properties)
  и [application-docker.properties](../server-site/src/main/resources/application-docker.properties) —
  `yookassa.webhook-token` (дефолтное значение закоммичено), пароли БД.
- [server-site/.../application.yml](../server-site/src/main/resources/application.yml) —
  реальные ФИО и **ИНН** самозанятого (персональные данные).
- [TaipanAuthTg/.../config.yml](../TaipanAuthTg/src/main/resources/config.yml) — креды БД (`tgbot/tgbot`).

**Усугубляющий фактор:** `.gitignore` пустые у `TgBot`, `AstronexisCore`, `TaipanAuthTg` —
при коммите секреты попадут в историю.

**Действия (срочно):**
1. **Отозвать и перевыпустить bot-token** через @BotFather (он уже скомпрометирован — лежит в файле).
2. Сменить пароли всех БД.
3. Вынести секреты в переменные окружения / секрет-менеджер; в репозитории оставить только
   `*.example`-шаблоны с плейсхолдерами.
4. Заполнить `.gitignore` (`.env`, `config.yaml`, `application-*.properties` с секретами).
5. Если репозиторий уже публиковался — почистить историю (`git filter-repo`) и считать токены утёкшими.

**Фикс:** `SEC-2` в TODO.

---

## 🔴 SEC-3. Нет фулфилмента → оплата без выдачи товара (риск для репутации/возвратов)

**Где:** [server-site/.../service/PaymentService.java](../server-site/src/main/java/dev/taipan/server_site/service/PaymentService.java),
webhook.

**Суть:** после `SUCCEEDED` ничего не выдаётся на сервере (нет RCON/моста к плагину прав).
Игрок платит за VIP и не получает его автоматически. Это не «взлом», но прямой
финансовый/репутационный риск и почва для чарджбэков.

**Фикс:** `SITE-3` в TODO.

---

## 🟠 SEC-4. Пробелы в «заморозке» неавторизованного игрока

**Где:** [TaipanAuthTg/.../gate/GateListener.java](../TaipanAuthTg/src/main/java/dev/taipan/auth_tg/gate/GateListener.java)

**Суть:** `GateListener` блокирует движение, урон, голод, возгорание и команды, но **не**
обрабатывает:
- `AsyncPlayerChatEvent` — незалогиненный игрок может писать в чат;
- `PlayerInteractEvent`, `BlockBreakEvent`, `BlockPlaceEvent` — взаимодействие с миром;
- `PlayerDropItemEvent`, `InventoryClickEvent` — манипуляции с инвентарём.

**Риск:** обход части ограничений до прохождения 2FA (спам в чат, частичное взаимодействие).

**Фикс:** `AUTH-1` в TODO.

---

## 🟠 SEC-5. Нет ограничения попыток ввода 2FA-кода

**Где:** `GateListener.tryAcceptCode`.

**Суть:** 6-значный код (1e6 вариантов) с TTL 120с принимается без счётчика попыток и
лок-аута. Брутфорс через команды чата непрактичен, но защита по глубине отсутствует.

**Фикс:** `AUTH-2` в TODO.

---

## 🟠 SEC-6. Нет CSRF-защиты на платёжных формах сайта

**Где:** [DonateController](../server-site/src/main/java/dev/taipan/server_site/controller/DonateController.java)
(`POST /donate`, `POST /vip`).

**Суть:** Spring Security не подключён, CSRF-токенов нет. Сторонний сайт может отправить
форму от имени пользователя. Для публичных платёжных форм риск ограничен (платит инициатор),
но эндпоинты открыты для автоматического спама/создания мусорных `PENDING`-платежей.

**Сопутствующее:** нет rate-limiting на создание платежей.

**Фикс:** `SITE-4` в TODO.

---

## 🟡 SEC-7. Прочее

- **devtools в проде:** `spring-boot-devtools` в зависимостях — не должен попадать в
  production-сборку. (`SITE-6`)
- **Курсы валют захардкожены** (`fx.*`) — не уязвимость, но при росте курса можно
  «недоплатить» относительно реальной стоимости. (`SITE-5`)
- **Логирование:** проверить, что токены/коды/креды не попадают в логи (`messagePrefix`,
  ответы ЮKassa). (`OPS-2`)
- **Зависимости:** зафиксировать и регулярно обновлять (Dependabot/renovate). AuthMe — форк
  `AuthMe-5.7.0-FORK-Universal.jar` лежит в `libs/`; убедиться в его происхождении. (`OPS-3`)

---

## Сводная таблица

| ID | Серьёзность | Кратко | Модуль |
|---|---|---|---|
| SEC-1 | 🔴 | Подделка платежей в webhook | server-site |
| SEC-2 | 🔴 | Секреты в репозитории | все |
| SEC-3 | 🔴 | Нет фулфилмента платежей | server-site |
| SEC-4 | 🟠 | Пробелы заморозки игрока | TaipanAuthTg |
| SEC-5 | 🟠 | Нет лимита попыток 2FA | TaipanAuthTg |
| SEC-6 | 🟠 | Нет CSRF/rate-limit на формах | server-site |
| SEC-7 | 🟡 | devtools, курсы, логи, зависимости | разное |
