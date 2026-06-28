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
│   ├── AuthMePoller.java           # поллинг AuthMeApi.isAuthenticated() раз в секунду
│   └── AuthMeHook.java             # (заглушка)
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
3. Игрок вводит `/tgcode <код>` → `tryAcceptCode`: при совпадении создаёт trust-сессию
   (fingerprint + `trustTtlSeconds`) и размораживает.

«Заморозка» (`mustBeFrozen`) отменяет перемещение, урон, голод, возгорание и все команды,
кроме `/login /register /auth /tgcode`.

## 🟢 Сильные стороны

- Криптостойкая генерация кода (`SecureRandom`), TTL, trust-сессии с привязкой к отпечатку.
- Абстракция хранилищ: `BindingsStore`/`TrustStore` с реализациями Postgres и YAML,
  переключение одним параметром `storage.type`.
- Аккуратные `onDisable` с закрытием ресурсов, поддержка Bedrock-форм.
- Bypass-право для админов/отладки.

## 🔴🟠🟡 Слабые места / TODO

| ID | Severity | Проблема |
|---|---|---|
| [SEC-4 / AUTH-1](../TODO.md) | 🟠 | Заморозка не покрывает чат, взаимодействие с миром/блоками, drop/inventory |
| [SEC-5 / AUTH-2](../TODO.md) | 🟠 | Нет лимита попыток и лок-аута при вводе `/tgcode` |
| [AUTH-3](../TODO.md) | 🟠 | Авторизация на поллинге (1с) вместо событий AuthMe → задержка + нагрузка. Рассмотреть `LoginEvent`/`LogoutEvent` AuthMe |
| [ARCH-1](../TODO.md) | 🟠 | Источник правды `bindings` общий с TgBot — синхронизировать схему/подключение |
| [SEC-2](../TODO.md) | 🔴 | Креды БД (`tgbot/tgbot`) и плейсхолдер токена в `config.yml` |
| [AUTH-4](../TODO.md) | 🟡 | `AuthMeHook.java` — пустая заглушка, удалить или реализовать |
| [AUTH-5](../TODO.md) | 🟡 | `GateListener` ~310 строк смешивает состояние, события и заморозку — разнести |
| [AUTH-6](../TODO.md) | 🟡 | Нет тестов; код подтверждается только Telegram-доставкой (нет фолбэка при сбое TG) |

## Как собрать

`cd TaipanAuthTg && ./gradlew jar` → jar в `build/libs/`. Требует установленного AuthMe
на сервере (форк лежит в `libs/AuthMe-5.7.0-FORK-Universal.jar`).
