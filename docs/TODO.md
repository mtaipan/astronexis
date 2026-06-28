# TODO — пошаговый план исправлений Astronexis

Главный рабочий документ. Чиним сверху вниз: 🔴 критично → 🟠 важно → 🟡 улучшения.
Каждая задача — чек-бокс. Отмечай `- [x]` по мере выполнения.

Идентификаторы сквозные и встречаются в [SECURITY.md](SECURITY.md) и в `modules/*`.

**Легенда:** `M` = модуль · `S` = серьёзность · файлы кликабельны.

---

## 🔴 Этап 0. Срочно (безопасность, до любого деплоя)

### SEC-2 · Вынести секреты из репозитория и отозвать токен · `все`
- [ ] ⚠️ **(твоё действие)** Отозвать текущий Telegram bot-token через @BotFather, выпустить новый.
- [ ] ⚠️ **(твоё действие)** Сменить пароли всех Postgres (`server_site`, `tgbot`).
- [x] Перевести секреты на переменные окружения / секрет-менеджер:
  - [x] `TgBot`: создан `.env.example`; мёртвый `config.yaml` с токеном удалён; токен в `.env` затёрт.
  - [x] `server-site`: `yookassa.*` и `spring.datasource.password` → `${ENV:...}`; создан `server-site/.env.example`.
  - [x] `TaipanAuthTg`: `config.yml` → плейсхолдеры (`CHANGE_ME`).
- [x] Убрать ПДн (ФИО, ИНН) из `application.yml`/`SiteProperties.java`/шаблонов → в ENV (`${PAYEE_*}`).
- [x] Заполнить `.gitignore` (корневой + правила для `.env`, `config.yaml`, `application-*.properties`).
- [ ] ⚠️ **(твоё действие)** Если репо публиковалось — почистить историю (`git filter-repo`) и считать секреты утёкшими.

### SEC-1 · Безопасный webhook ЮKassa · `server-site` ✅ (код)
Файл: [YooKassaWebhookController.java](../server-site/src/main/java/dev/taipan/server_site/controller/YooKassaWebhookController.java), [YooKassaClient.java](../server-site/src/main/java/dev/taipan/server_site/yookassa/YooKassaClient.java)
- [x] Добавлен `YooKassaClient.getPayment(id)` (`GET /v3/payments/{id}` под Basic-credentials).
- [x] Webhook берёт из тела только `id`, статус/сумму/`metadata` читает из ответа API.
- [x] Сверка суммы (`amount.value` vs `amountRub`), идемпотентность, 502 при сбое проверки (ЮKassa повторит).
- [x] Компилируется (`mvnw compile` → BUILD SUCCESS).
- [ ] (Доп. слой) ограничить источник по официальным IP-диапазонам ЮKassa.
- [ ] Тест: подделанный POST с `status=succeeded` без реального платежа не должен менять статус (после восстановления тестов — TESTS).

### SEC-3 / SITE-3 · Фулфилмент платежей в игру · `server-site` ✅ (код) — см. [ADR-2](DECISIONS.md)
Файлы: [FulfillmentService](../server-site/src/main/java/dev/taipan/server_site/service/FulfillmentService.java),
[PaymentConfirmationService](../server-site/src/main/java/dev/taipan/server_site/service/PaymentConfirmationService.java),
[RconClient](../server-site/src/main/java/dev/taipan/server_site/rcon/RconClient.java),
[VipGrant](../server-site/src/main/java/dev/taipan/server_site/model/VipGrant.java), `V3__vip_grants.sql`
- [x] **Transactional outbox**: при оплате VIP создаётся durable-грант `vip_grants` (idемпотентно по `payment_id`).
- [x] **Командой**: доставка RCON-командой (шаблон `rcon.vip-command`, по умолчанию LuckPerms).
- [x] **Другим способом**: очередь грантов + ретраи планировщиком (переживает оффлайн/сбой RCON);
      ту же таблицу может читать игровой плагин.
- [x] «Игрок оффлайн» закрыт ретраями; журнал выдач — в `vip_grants` (status/attempts/last_error) + логи.
- [x] Компилируется (BUILD SUCCESS).
- [ ] ⚠️ **(опц., деплой)** Включить `RCON_ENABLED=true` + креды RCON; либо написать плагин-консьюмер `vip_grants`.

---

## 🟠 Этап 1. Корректность и надёжность

### BOT-2 · Конфликт двух MessageHandler · `TgBot` ✅
Файл: [main.py](../TgBot/src/app/main.py), [text_router.py](../TgBot/src/app/bot/handlers/text_router.py)
- [x] Заменено двумя обработчиками на один фильтр → единый `text_router`, который разводит
      сообщение по `context.user_data["step"]` (bind / support). guard вызывается один раз.
- [x] Синтаксис проверен (`py_compile`). Полный рантайм-тест — после восстановления venv.

### SITE-1 · Обработка ошибок создания платежа · `server-site` ✅
Файл: [DonateController.java](../server-site/src/main/java/dev/taipan/server_site/controller/DonateController.java), [pay_error.html](../server-site/src/main/resources/templates/pay_error.html)
- [x] `redirect:null` устранён: при отсутствии `confirmation_url` → страница `pay_error`.
- [x] Исключения `YooKassaClient`/сервиса перехватываются → дружелюбное сообщение.
- [x] Валидация суммы (1…1 000 000 ₽) в `PaymentService`; валюта валидируется `FxService` (белый список).

### SEC-4 / AUTH-1 · Полная заморозка неавторизованного игрока · `TaipanAuthTg` ✅ (код)
Файл: [GateListener.java](../TaipanAuthTg/src/main/java/dev/taipan/auth_tg/gate/GateListener.java)
- [x] Добавлены обработчики (отмена при `mustBeFrozen`): чат (`AsyncPlayerChatEvent`),
      `PlayerInteractEvent`, `BlockBreakEvent`, `BlockPlaceEvent`, `PlayerDropItemEvent`,
      `InventoryClickEvent`, `InventoryOpenEvent`, `EntityPickupItemEvent`.
- [x] Разрешённые команды (`/login /register /auth /tgcode`) по-прежнему проходят (логика `onCmd` не тронута).
- [ ] ⚠️ Сборка не проверена в этом окружении (нет JDK ≤ 21 для Gradle, см. OPS-4). Проверено ревью.

### SEC-5 / AUTH-2 · Лимит попыток 2FA · `TaipanAuthTg` ✅ (код)
- [x] Счётчик неудачных `/tgcode` (`codeAttempts`); после `MAX_CODE_ATTEMPTS=5` код инвалидируется
      (`PendingCode.locked()`), игрок остаётся заморожен и должен перезайти/повторить `/login`.
- [ ] (Опц.) логировать серии попыток для аудита.

### AUTH-3 · Уйти от поллинга AuthMe к событиям · `TaipanAuthTg`
Файл: [AuthMePoller.java](../TaipanAuthTg/src/main/java/dev/taipan/auth_tg/auth/AuthMePoller.java)
- [ ] Подписаться на события AuthMe (`LoginEvent`/`LogoutEvent`) вместо опроса раз в секунду.
- [ ] Поллинг оставить как fallback, если событий недостаточно.

### SITE-4 / SEC-6 · CSRF и rate-limit на формах · `server-site`
- [ ] Подключить минимальную защиту от спама создания платежей (rate-limit по IP/нику).
- [ ] Решить по CSRF (если будут формы с состоянием пользователя) — добавить Spring Security/токены.

### BOT-1 · Один источник конфигурации · `TgBot`
- [ ] Выбрать ENV (`core/config.py`) как единственный источник; `config.yaml` либо удалить,
      либо сделать его явным загрузчиком, читаемым тем же `load_config`.
- [ ] Убрать дубль значений (admins, token, пути).

### ARCH-1 · Единый источник правды для `bindings` · `TgBot` + `TaipanAuthTg`
- [ ] Зафиксировать: привязки живут в Postgres (а не в `bindings.yml`).
- [ ] Привести `TgBot/config.yaml` (путь к `bindings.yml`) и `TaipanAuthTg` `storage.type`
      к одной БД/схеме; описать связь в [ARCHITECTURE.md](ARCHITECTURE.md).
- [ ] Миграция существующих YAML-привязок в Postgres (если есть прод-данные).

### BOT-3 · Гарантировать guard на всех хендлерах · `TgBot` ✅ — см. [ADR-5](DECISIONS.md)
- [x] Добавлен декоратор `@require_access` ([guard.py](../TgBot/src/app/bot/middlewares/guard.py)).
- [x] Все хендлеры переведены на декоратор, ручные `if not await guard(...)` убраны.
- [x] Синхронный `is_admin` не декорируется; `py_compile` + аудит импортов — чисто.

### ARCH-2 · Завершить миграцию меню и удалить дубль · `AstronexisCore` ✅
- [x] Сверён паритет: страницы меню, телепорты (survival/hub), компас (PDC `menu_compass`),
      cooldown, тексты справки (`help.privates`/`help.commands` в i18n) — всё есть в Core, плюс
      Bedrock-рендер, action bar, i18n.
- [x] `TaipanMenuPlugin` удалён из репозитория (10 .java).
- [x] Теперь `/menu` регистрирует только `AstronexisCore`.
- [ ] ⚠️ **(деплой)** Убедиться, что на сервере в `plugins/` лежит только `AstronexisCore`, а старый
      `TaipanMenuPlugin.jar` удалён.

### ARCH-3 · Не ставить оба плагина меню · `деплой` ✅
- [x] Неактуально: `TaipanMenuPlugin` удалён, конфликта `/menu` больше нет.

---

## 🟡 Этап 2. Качество, чистка, тесты

### OPS-1 · Чистка репозитория · `все`
- [x] Удалить `*Zone.Identifier` (1686 файлов) и `.DS_Store`.
- [x] Настроить корневой `.gitignore` (покрывает `.idea/`, `.gradle/`, `.venv/`, `build/`, `target/`).
- [ ] При инициализации git убедиться, что `.idea/`, `.gradle/`, `.venv/`, `libs/*.jar` не закоммичены.

### OPS-2 · Аудит логирования · `все`
- [ ] Убедиться, что токены/коды/креды/ответы ЮKassa не попадают в логи.

### OPS-3 · Управление зависимостями · `все`
- [ ] Зафиксировать версии, подключить Dependabot/renovate.
- [ ] Проверить происхождение `AuthMe-5.7.0-FORK-Universal.jar` и `floodgate-spigot.jar` в `libs/`.

### SITE-5 · Курсы валют · `server-site`
- [ ] Вынести `fx.*` в настраиваемый источник или подтягивать актуальные курсы; документировать политику.

### SITE-6 · Убрать devtools из прод-сборки · `server-site`
- [ ] `spring-boot-devtools` → `optional`/только dev-профиль.

### SITE-2 · Приватность статуса платежа · `server-site`
- [ ] Решить, нужно ли скрывать детали `/pay/return?pid=` (показывать минимум, без чужих данных).

### SITE-8 · Порядок миграций · `server-site`
- [ ] Разобраться с пустой `V2__platform.sql` (убрать заглушку или наполнить осмысленно).

### BOT-4 · Legacy YAML-сторы · `TgBot` ✅
- [x] Пакет `app/storage/*_yaml.py` — мёртвый код (никто не импортировал), удалён.

### BOT-5 · Доделать/убрать nickchange · `TgBot` ✅
- [x] Закомментированная регистрация `nickchange_cmd` удалена из `main.py`.

### BOT-7 · Разбить admin.py · `TgBot`
- [ ] ~400 строк → разнести по областям (channels / bans / bindings / tickets / price).

### AUTH-4 · Убрать заглушку · `TaipanAuthTg` ✅
- [x] Пустой `AuthMeHook.java` удалён (ссылок не было).

### OPS-4 · Сборка плагинов требует JDK ≤ 21 · `плагины`
- [ ] В этом окружении стоит только JDK 26 — Gradle 8.8 на ней не стартует. Для сборки
      `TaipanAuthTg`/`AstronexisCore` нужен JDK 17–21 (toolchain в build.gradle = 21).
      Изменения в плагинах пока проверены ревью, не компиляцией.

### AUTH-5 · Рефактор GateListener · `TaipanAuthTg`
- [ ] Разнести состояние (pending/trust), обработку событий и логику заморозки по классам.

### AUTH-6 · Фолбэк доставки кода · `TaipanAuthTg`
- [ ] Обработать сбой отправки в Telegram (ретрай/сообщение игроку), не оставлять «вечную заморозку».

### CORE-1..4 · Ядро · `AstronexisCore`
- [ ] CORE-1: вынести `ActionBarService` из `MenuModule` в самостоятельный сервис/модуль.
- [ ] CORE-2: перейти на Adventure API вместо ручной замены `&`→`§`.
- [ ] CORE-3: валидация значений конфига; базовые тесты.
- [ ] CORE-4: задокументировать шаблон добавления нового `CoreModule`.

### TESTS · Тестовое покрытие · `все`
- [ ] `server-site`: тесты webhook (после SEC-1), PaymentService, FxService, NickValidator.
- [ ] `TgBot`: тесты BindingsService (правила уникальности), AccessService, роутинга хендлеров.
- [ ] `TaipanAuthTg`: тесты Codes/Fingerprints, логики Gate (где возможно без сервера).

---

## Рекомендуемый порядок выполнения

1. **SEC-2** (отозвать токен, вынести секреты) — пока не сделано, остальное вторично.
2. **SEC-1** + **SEC-3/SITE-3** — платежи: подтверждение + выдача товара.
3. **BOT-2**, **SITE-1**, **SEC-4/AUTH-1** — явные функциональные баги/дыры.
4. **ARCH-1**, **ARCH-2**, **BOT-1** — навести порядок в источниках правды и дублях.
5. Остальное из Этапа 1, затем Этап 2.

> Прогресс удобно отмечать прямо здесь. Когда закрыт целый этап — отметь это в начале раздела.
