# Безопасность Astronexis

Список уязвимостей и рисков с оценкой и привязкой к задачам в [TODO.md](TODO.md).
Отсортировано по серьёзности. Раздел **«Найдено при независимом ревью»** — то, чего в
прошлой версии этого документа не было; это работающие дыры, а не гипотезы.

> **Как читать статусы:** ✅ (код) = исправление написано и проверено ревью/компиляцией, но
> **не** прогнано вживую и **не** покрыто тестом. Это не то же самое, что «безопасно». См.
> [STATUS.md](STATUS.md).

---

## Найдено при независимом ревью (не было в прошлой версии)

### 🔴 SEC-8. Обход 2FA: достаточно подождать TTL кода

**Где:** [GateListener.isBlocked / isFullyAuthorized](../TaipanAuthTg/src/main/java/dev/taipan/auth_tg/gate/GateListener.java) (строки ~119–142).

**Суть.** Заморозка игрока держится на наличии записи в `pending`. В `isBlocked()` при
истечении TTL кода запись **удаляется**, а метод возвращает `false`:

```java
if (Instant.now().isAfter(pc.expiresAt)) {
    pending.remove(p.getUniqueId());   // код истёк → запись убрана
    return false;                       // → isBlocked() == false
}
```

`isFullyAuthorized = isAuthMeLoggedIn && !isBlocked`. После истечения кода `isBlocked()`
даёт `false`, значит `isFullyAuthorized()` даёт `true`, значит `mustBeFrozen()` даёт `false` —
игрока **размораживает**.

**Эксплуатация.** Атакующий, узнавший пароль AuthMe (но не имеющий доступа к Telegram
жертвы), заходит на сервер, получает запрос кода, **ничего не вводит и ждёт TTL (120с)** —
после чего гейт его пропускает. Второй фактор становится необязательным. Это дыра ровно в
том, ради чего существует весь модуль. SEC-6 (ниже) говорил про брутфорс кода, но перебор
не нужен — код можно просто переждать.

**Фикс.** Истечение кода должно вести в состояние «требуется повторный `/login`», а не в
«авторизован». `isBlocked()` при `expired` обязан оставаться `true` (или гейт должен
опираться на позитивный признак «trust-сессия валидна», а не на отсутствие `pending`).
Задача `AUTH-7` в TODO.

> **Статус: ✅ исправлено (код + тест).** Гейт переведён на позитивный признак
> (`tgVerified`: валидная trust-сессия при входе ИЛИ принятый `/tgcode`); истечение TTL
> переводит код в `locked` и оставляет заморозку, новый код выдаётся по повторному `/login`.
> Регрессия покрыта `GateListenerTest.expiredCodeKeepsPlayerFrozen` (Gradle-тесты плагина
> проходят на JDK 21). См. `AUTH-7`.

---

### 🔴 SEC-9. Половина админ-панели бота не подключена (сломанная функциональность)

**Где:** [TgBot/src/app/main.py](../TgBot/src/app/bot/handlers/../main.py) vs
[handlers/admin.py](../TgBot/src/app/bot/handlers/admin.py).

**Суть.** В `admin.py` реализованы `channels_cmd`, `channel_add/del`, `subs_cmd`, `ban_cmd`,
`unban_cmd`, `price_get/set_cmd`, `bind_get/set/del/del_nick_cmd`, `find_nick/tg/name_cmd`.
В `main.py` из них зарегистрированы **только** `admin`, `tickets`, `reply`, `close`.
Остальные `CommandHandler` нигде не добавляются в `Application` — команды из меню `/admin`
существуют как код, но бот на них не реагирует.

**Последствие.** Заявленные функции модерации — **бан, управление каналами обязательной
подписки, ручные привязки, поиск игроков, цена смены ника** — не работают в проде. Это не
уязвимость в классическом смысле, а сломанная функциональность безопасности (нельзя
забанить нарушителя, нельзя включить обязательную подписку через `/subs`).

**Фикс.** Зарегистрировать недостающие `CommandHandler` в `main.py` (или, лучше, собрать их
списком и добавить циклом, чтобы новые команды нельзя было забыть). Задача `BOT-8`.

> **Статус: ✅ исправлено (код + тест).** Все 15 команд зарегистрированы списком
> `ADMIN_COMMANDS` циклом; `tests/test_admin_registration.py` сверяет команды из текста
> меню `/admin` с реально зарегистрированными и ловит «объявлен, но не подключён». См. `BOT-8`.

---

### 🟠 SEC-10. Гонка двойной выдачи VIP (нет блокировки при доставке)

**Где:** [FulfillmentService.deliver](../server-site/src/main/java/dev/taipan/server_site/service/FulfillmentService.java),
вызывается из [webhook](../server-site/src/main/java/dev/taipan/server_site/controller/YooKassaWebhookController.java)
и [VipGrantRetryScheduler](../server-site/src/main/java/dev/taipan/server_site/service/VipGrantRetryScheduler.java).

**Суть.** После оплаты webhook немедленно вызывает `fulfillment.tryDeliver(grant)`, а
планировщик каждые 2 минуты параллельно тянет `deliverPending()`. `deliver()` проверяет
`g.getStatus() != PENDING` в памяти и **не** берёт блокировку строки (`SELECT … FOR UPDATE`).
Два потока (webhook + планировщик, либо два повторных webhook от ЮKassa) могут прочитать
`PENDING` одновременно и оба выполнить RCON-команду `lp … addtemp` — VIP выдастся дважды
(удвоенный срок).

**Почему outbox тут не спасает.** Идемпотентность есть на уровне *создания* гранта
(`uq_vip_grants_payment`), но не на уровне *доставки*.

**Фикс.** Забирать гранты `SELECT … FOR UPDATE SKIP LOCKED` (или атомарный
`UPDATE … SET status='DELIVERING' WHERE id=? AND status='PENDING'` с проверкой affected rows)
перед RCON-вызовом. Задача `SITE-9`.

> **Статус: ✅ исправлено (код + тест).** Атомарный захват `claimForDelivery`
> (PENDING → DELIVERING + attempts+1 одним UPDATE, проверка affected rows) до RCON-вызова;
> зависшие DELIVERING планировщик возвращает в очередь через 10 мин (`V4__vip_grants_claim.sql`).
> Гонка покрыта `FulfillmentServiceTest.parallelDeliveryOfSameGrantSendsExactlyOneRconCommand`
> (6/6 тестов, `mvnw test`). См. `SITE-9`.

---

### 🟠 SEC-11. Блокирующий JDBC в главном потоке игрового сервера

**Где:** [AuthMePoller.tick](../TaipanAuthTg/src/main/java/dev/taipan/auth_tg/auth/AuthMePoller.java)
→ [GateListener.onAuthMeLogin](../TaipanAuthTg/src/main/java/dev/taipan/auth_tg/gate/GateListener.java)
→ `store.getChatIdByNick` / `trust.getValid`.

**Суть.** Поллер работает через `scheduleSyncRepeatingTask` — это **главный тик-поток**
сервера. При входе игрока `onAuthMeLogin` синхронно ходит в Postgres через Hikari
(`getChatIdByNick`, `getValid`). Кэш на 10с смягчает, но при первом входе или промахе кэша
сетевой round-trip к БД выполняется в тике. Если БД медленная/недоступна — лаги или фриз
всего сервера (не только этого игрока).

**Риск.** Не «взлом», а доступность: деградация БД → деградация игрового сервера. Для
auth-плагина это ещё и вектор DoS (нагрузить БД → повесить тик).

**Фикс.** Выносить обращения к БД в async-задачу (`runTaskAsynchronously`), результат
применять обратно в основном потоке. Задача `AUTH-8`.

> **Статус: ✅ исправлено (код).** `trust.getValid` + `getChatIdByNick` ушли в
> `runTaskAsynchronously`, результат применяется в основном потоке; `trust.upsert` тоже async.
> Пока async-проверка в полёте, гейт закрыт (позитивная модель из AUTH-7 делает это безопасным).
> См. `AUTH-8`.

---

### 🟡 SEC-12. Telegram-код уходит GET-запросом «выстрелил и забыл»

**Где:** [TelegramApi.sendMessage](../TaipanAuthTg/src/main/java/dev/taipan/auth_tg/tg/TelegramApi.java).

**Суть.** 2FA-код передаётся как `?text=Код входа: 123456` в query-строке GET-запроса,
и отправка идёт `sendAsync(..., BodyHandlers.discarding())` — ответ игнорируется. Проблемы:
1. При ошибке/429 от Telegram код **молча не доставляется**, игрок заперт без объяснения
   (пересекается с AUTH-6 «нет фолбэка доставки»).
2. Полный URL с bot-token и кодом попадает в любые промежуточные логи/трейсы клиента.

TLS шифрует тело, так что по сети код не перехватить, — риск ограничен, отсюда 🟡.

**Фикс.** POST с телом, проверка HTTP-статуса ответа, обработка сбоя (сообщить игроку /
ретрай). Задача `AUTH-9`.

> **Статус: ✅ исправлено (код).** `sendMessage` теперь POST с телом и таймаутами, статус
> ответа проверяется; при сбое — warning в лог плагина и сообщение игроку
> («не удалось отправить код, повтори /login позже»). См. `AUTH-9`.

---

### 🟡 SEC-13. Проверка подписки на каналы — fail-open

**Где:** [AccessService.check](../TgBot/src/app/services/access_service.py) +
`SubscriptionsService`.

**Суть.** Если список каналов пуст или проверка членства падает — доступ **разрешается**.
Это осознанный компромисс (не блокировать пользователей из-за ошибки Telegram API), но его
надо зафиксировать как принятый риск: включённый `enforce_subscriptions` не гарантирует
блокировку при сбоях. Задача — задокументировать (`BOT-9`), не обязательно менять поведение.

> **Статус: ✅ решено и зафиксировано (код + тест).** Поправка: код был fail-open только для
> пустого списка каналов (это оставлено и задокументировано как осознанное «нечего
> проверять»). Сбой Telegram API был **fail-closed с разрушительным побочным эффектом**:
> ошибка считалась «не подписан» и при `delete_binding_on_unsub=true` удаляла привязку
> игрока. Теперь `is_subscribed` различает `False` (точно не подписан) и `None` (проверка
> не удалась): при `None` — отказ в доступе, но привязка сохраняется. Политика покрыта
> `tests/test_access_service.py`. См. `BOT-9`.

---

## Ранее заявленные (проверено по коду)

## 🟢 SEC-1. Подделка платежей через webhook ЮKassa — исправлено (код)

**Где:** [YooKassaWebhookController.java](../server-site/src/main/java/dev/taipan/server_site/controller/YooKassaWebhookController.java).

Webhook больше не доверяет телу: из уведомления берётся только `id`, реальный статус/сумма
запрашиваются `GET /v3/payments/{id}` под Basic-credentials, применяется только ответ API.
Есть сверка суммы, идемпотентность по статусу, 502 при сбое проверки (ЮKassa повторит).
Статический query-token оставлен как дешёвый фильтр от спама. **Открыто:** ограничение по
официальным IP-диапазонам ЮKassa (доп. слой) и тест на подделку. См. `SEC-1` в TODO, [ADR-1](DECISIONS.md).

---

## 🟢 SEC-2. Секреты в репозитории — исправлено, угроза переоценена

> **Статус:** живые секреты вынесены в ENV/плейсхолдеры; `.env` содержит плейсхолдер
> (`BOT_TOKEN=PUT_...`), мёртвый `config.yaml` удалён, `.gitignore` заполнен.

**Важная поправка к прошлой версии.** Git-история этого репозитория состоит из **2 коммитов**,
и `git log` по `.env`/`config.yaml` **пуст** — секреты в историю никогда не попадали.
Поэтому пункт «почистить историю через `git filter-repo`» для *этого* репозитория не нужен.

**Остаётся за тобой (ручное):**
1. Перевыпустить bot-token через @BotFather — он лежал в рабочей копии, считать
   скомпрометированным на всякий случай.
2. Сменить пароли БД (`server_site`, `tgbot`), которые фигурировали в старых конфигах.
3. Не публиковать `.env` (уже в `.gitignore`).

См. `SEC-2` в TODO, [ADR-3](DECISIONS.md).

---

## 🟢 SEC-3. Нет фулфилмента платежей — исправлено (код)

**Где:** [PaymentConfirmationService](../server-site/src/main/java/dev/taipan/server_site/service/PaymentConfirmationService.java),
[FulfillmentService](../server-site/src/main/java/dev/taipan/server_site/service/FulfillmentService.java).

Реализован transactional outbox: при оплате VIP создаётся durable-грант `vip_grants`
(идемпотентно по `payment_id`), доставка идёт RCON-командой вне транзакции, невыданные
гранты дотягивает планировщик. Гонка двойной доставки (SEC-10) закрыта атомарным захватом.
**Открыто:** RCON по умолчанию выключен, плагин-консьюмер `vip_grants` не написан.
См. `SITE-3`, [ADR-2](DECISIONS.md).

---

## 🟢 SEC-4. Пробелы «заморозки» неавторизованного игрока — исправлено (код)

**Где:** [GateListener](../TaipanAuthTg/src/main/java/dev/taipan/auth_tg/gate/GateListener.java).

Теперь при `mustBeFrozen` отменяются чат (`AsyncPlayerChatEvent`), интеракции, ломание/
постановка блоков, дроп, клики/открытие инвентаря и подбор предметов. Оговорка про SEC-8
(обход по TTL размораживал игрока) снята — SEC-8 исправлен, `mustBeFrozen` держится на
позитивном признаке авторизации. См. `SEC-4`/`AUTH-1`.

---

## 🟢 SEC-5 (было SEC-5). Лимит попыток 2FA-кода — исправлено (код)

**Где:** `GateListener.tryAcceptCode`.

После 5 неверных вводов код инвалидируется (`PendingCode.locked()`), игрок остаётся
заморожен до повторного `/login`. Закрывает брутфорс; SEC-8 («код пережидают») закрыт
отдельно в AUTH-7. Оба сценария покрыты `GateListenerTest`. См. `AUTH-2`.

---

## 🟠 SEC-6. Нет CSRF-защиты и rate-limit на платёжных формах

**Где:** [DonateController](../server-site/src/main/java/dev/taipan/server_site/controller/DonateController.java)
(`POST /donate`, `POST /vip`).

Spring Security не подключён, CSRF-токенов нет, rate-limit нет. Для публичных платёжных форм
риск ограничен (платит инициатор), но эндпоинты открыты для спама мусорных `PENDING`-платежей.
Не исправлено. См. `SITE-4`.

---

## 🟡 SEC-7. Прочее

- **devtools в проде:** `spring-boot-devtools` в зависимостях ([pom.xml](../server-site/pom.xml)) —
  не должен попадать в production-сборку. (`SITE-6`) — не исправлено.
- **Курсы валют захардкожены** (`fx.*`) — при росте курса можно «недоплатить». (`SITE-5`)
- **Логирование:** проверить, что токены/коды/креды не попадают в логи. (`OPS-2`)
- **Зависимости и `libs/*.jar`:** форк `AuthMe-5.7.0-FORK-Universal.jar` и `floodgate-spigot.jar`
  лежат бинарниками — происхождение не проверено (`OPS-3`).

---

## Сводная таблица

| ID | Серьёзность | Кратко | Модуль | Статус |
|---|---|---|---|---|
| SEC-8 | 🔴 | Обход 2FA: переждать TTL кода → пускает | TaipanAuthTg | ✅ исправлено (код + тест) |
| SEC-9 | 🔴 | Половина админ-команд бота не зарегистрирована | TgBot | ✅ исправлено (код + тест) |
| SEC-10 | 🟠 | Гонка двойной выдачи VIP (нет блокировки) | server-site | ✅ исправлено (код + тест) |
| SEC-11 | 🟠 | Блокирующий JDBC в тик-потоке сервера | TaipanAuthTg | ✅ исправлено (код) |
| SEC-12 | 🟡 | 2FA-код GET fire-and-forget, без проверки доставки | TaipanAuthTg | ✅ исправлено (код) |
| SEC-13 | 🟡 | Подписки: fail-open только для пустого списка; сбой API не удаляет привязку | TgBot | ✅ решено (код + тест) |
| SEC-1 | 🟢 | Подделка платежей в webhook | server-site | исправлено (код) |
| SEC-2 | 🟢 | Секреты в репо (история чистая) | все | исправлено; ручное: отзыв токена |
| SEC-3 | 🟢 | Фулфилмент платежей | server-site | исправлено (код) |
| SEC-4 | 🟢 | Пробелы заморозки игрока | TaipanAuthTg | исправлено (код) |
| SEC-5 | 🟢 | Лимит попыток 2FA | TaipanAuthTg | исправлено (код) |
| SEC-6 | 🟠 | Нет CSRF/rate-limit на формах | server-site | открыто |
| SEC-7 | 🟡 | devtools, курсы, логи, зависимости | разное | открыто |
