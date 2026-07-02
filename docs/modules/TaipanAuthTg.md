# Модуль: TaipanAuthTg

Плагин двухфакторной авторизации поверх **AuthMe**: после входа по паролю игрок
подтверждает вход одноразовым кодом из Telegram (`/tgcode`). Поддерживает Java и Bedrock
(Floodgate-формы), Postgres и YAML-хранилища.

- **Стек:** Java 21, Spigot/Paper API 1.21, AuthMe API, Floodgate/Geyser (softdepend), Postgres.
- **Сборка:** Gradle (`./gradlew jar`).
- **Зависимости плагина:** `depend: AuthMe`, `softdepend: Floodgate, Geyser-Spigot`.

## Структура

```
TaipanAuthTg/src/main/java/dev/taipan/auth_tg/
├── TaipanAuthTgPlugin.java         # onEnable/onDisable, выбор сторов, регистрация
├── config/
│   ├── PluginConfig.java           # маппинг config.yml
│   ├── SecurityConfig, TelegramConfig, PostgresConfig
├── auth/
│   └── AuthMePoller.java           # поллинг AuthMeApi.isAuthenticated() раз в секунду
│                                   #   (SEC-11 закрыт: БД-вызовы теперь async)
├── gate/GateListener.java          # «заморозка» неавторизованных + приём кода
├── bedrock/
│   ├── BedrockAuthForms.java       # формы Floodgate
│   └── BedrockUiManager.java
├── store/
│   ├── BindingsStore / PgBindingsStore / YamlBindingsStore   # ник ↔ chatId
│   ├── TrustStore / PgTrustStore / YamlTrustStore            # доверенные сессии
│   ├── Binding.java, TrustSession.java
├── tg/TelegramApi.java             # отправка сообщений в Telegram
├── util/
│   ├── Codes.java                  # SecureRandom code6()
│   └── Fingerprints.java           # отпечаток по IP/подсети
└── cmd/
    ├── TgCodeCommand.java          # /tgcode <код>
    ├── AuthCommand.java            # /auth (Bedrock-форма)
    └── admin/AuthTgCommand.java    # /authtg (админ)
```

## Конфигурация (`config.yml`)

```yaml
storage: { type: postgres }          # yaml | postgres
telegram: { botToken, messagePrefix }
security:
  codeTtlSeconds: 120                 # TTL кода
  allowBypassPermission: false        # bypass по праву
  bypassPermissionNode: taipan.authtg.bypass
  trustTtlSeconds: 21600              # 6 ч доверия после кода
  trustBind: ip-subnet                # none | ip | ip-subnet
  trustSubnetV4: 24
postgres: { host, port, database, user, password, poolSize, cacheTtlSeconds }
```

## Поток авторизации

1. `AuthMePoller` каждую секунду опрашивает `AuthMeApi.isAuthenticated(player)`; при переходе
   `false→true` вызывает `GateListener.onAuthMeLogin(player, true)`.
2. `GateListener` решает:
   - bypass-право → пускает;
   - валидная trust-сессия (совпал fingerprint) → пускает;
   - нет привязки ник↔chatId → блок с инструкцией привязать TG;
   - иначе генерирует `code6()`, шлёт в Telegram, держит игрока «замороженным».
3. Игрок вводит `/tgcode <код>` → `tryAcceptCode`: при совпадении добавляет игрока в
   `tgVerified`, создаёт trust-сессию (fingerprint + `trustTtlSeconds`) и размораживает.

«Заморозка» (`mustBeFrozen`) отменяет перемещение, урон, голод, возгорание, чат,
интеракции, блоки, дроп/инвентарь/подбор и все команды, кроме `/login /register /auth /tgcode`.

> **✅ Исправлено (SEC-8 / AUTH-7).** Раньше истечение TTL кода удаляло `pending` и
> размораживало игрока **без ввода кода** — второй фактор обходился ожиданием. Теперь
> авторизация держится на позитивном признаке (`tgVerified`: валидная trust-сессия ИЛИ
> принятый код); истёкший код переходит в `locked`, заморозка сохраняется, новый код
> перевыпускается по `/login`. Регрессия покрыта `GateListenerTest`.
> БД-вызовы гейта ушли из тик-потока в async (AUTH-8), доставка кода проверяется (AUTH-9).

## 🟢 Сильные стороны

- Криптостойкая генерация кода (`SecureRandom`), TTL, trust-сессии с привязкой к отпечатку.
- Абстракция хранилищ: `BindingsStore`/`TrustStore` с реализациями Postgres и YAML,
  переключение одним параметром `storage.type`.
- Аккуратные `onDisable` с закрытием ресурсов, поддержка Bedrock-форм.
- Bypass-право для админов/отладки.

## 🔴🟠🟡 Слабые места / TODO

| ID | Severity | Проблема |
|---|---|---|
| [AUTH-7 / SEC-8](../TODO.md) | 🔴✅ | **Обход 2FA закрыт:** гейт на позитивном признаке `tgVerified`; истечение TTL держит заморозку. Тест: `GateListenerTest` |
| [AUTH-8 / SEC-11](../TODO.md) | 🟠✅ | JDBC ушёл из тик-потока: `getValid`/`getChatIdByNick`/`upsert` в `runTaskAsynchronously`, применение в main thread |
| [AUTH-9 / SEC-12](../TODO.md) | 🟡✅ | POST + проверка статуса; при сбое — лог + сообщение игроку (ретраи AUTH-6 — открыто) |
| [SEC-4 / AUTH-1](../TODO.md) | 🟢✅ | Заморозка расширена на чат/мир/инвентарь; оговорка про AUTH-7 снята |
| [SEC-5 / AUTH-2](../TODO.md) | 🟢✅ | Лимит 5 попыток `/tgcode`; сценарий «пережидание» закрыт AUTH-7. Оба покрыты тестами |
| [AUTH-3](../TODO.md) | 🟠 | Авторизация на поллинге (1с) вместо событий AuthMe → задержка + нагрузка. Рассмотреть `LoginEvent`/`LogoutEvent` |
| [ARCH-1 / ARCH-4](../TODO.md) | 🟠 | Плагин читает таблицу `bindings` сырым SQL, схему которой владеет TgBot — нет контракта |
| [SEC-2](../TODO.md) | 🟢✅ | Креды БД и токен в `config.yml` → плейсхолдеры (`CHANGE_ME`); ручное: сменить пароли |
| [AUTH-5](../TODO.md) | 🟡 | `GateListener` смешивает состояние, события и заморозку — разнести (см. [ADR-10](../DECISIONS.md)) |
| [AUTH-6](../TODO.md) | 🟡 | Ретраев доставки кода нет (сбой уже логируется и сообщается игроку — AUTH-9); тесты гейта есть (`GateListenerTest`), остальное не покрыто |

## Как собрать

`cd TaipanAuthTg && JAVA_HOME=/opt/homebrew/opt/openjdk@21 sh gradlew build` → jar в
`build/libs/`, тесты гоняются автоматически (Gradle требует JDK ≤ 21, см. OPS-4 в TODO).
Требует установленного AuthMe на сервере (форк лежит в `libs/AuthMe-5.7.0-FORK-Universal.jar`).
