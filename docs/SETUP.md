# Запуск и сборка Astronexis

Инструкции для локальной разработки. Перед деплоем обязательно прочитай
[SECURITY.md](SECURITY.md) — секреты сейчас в репозитории.

## Предварительные требования

- JDK 21 (для плагинов и сайта).
- Python 3.12 (для бота).
- PostgreSQL 16 (две логические БД: `server_site` для сайта, `tgbot` для бота/авторизации).
- Docker + docker-compose (опционально).
- Сервер Paper/Spigot 1.21.1 с установленным AuthMe (для плагинов).

---

## Серверные плагины (для Java-части)

Проверено по `build.gradle` и `plugin.yml` — astronexis-плагинам нужно немного:

| Плагин | Зачем | Где задействован |
|---|---|---|
| **Paper** 1.21.x | платформа сервера | оба плагина (`compileOnly paper-api` в `build.gradle`) |
| **Geyser + Floodgate** | поддержка Bedrock-игроков | `TaipanAuthTg` рендерит формы авторизации для Bedrock через Floodgate API (`build.gradle` → `libs/floodgate-spigot.jar`) |
| **AuthMe** | базовая авторизация | `TaipanAuthTg` надстраивает 2FA поверх AuthMe (`AuthMePoller` опрашивает состояние логина) |

`AstronexisCore` плагинов-зависимостей **не объявляет** (только Paper; `depend`/`softdepend` в его `plugin.yml` пустые).

> **Не используются сейчас** (вопреки старым черновикам архитектуры): LuckPerms, PlaceholderAPI,
> ProtocolLib, ViaVersion/ViaBackwards, Spark, CoreProtect, LiteBans, BlueMap — в коде их нет.
> Это **вишлист на будущее**, а не зависимости. Целевое расширение плагинов и модулей внутри
> Core — в [ROADMAP.md](ROADMAP.md), не здесь: документируем то, что есть, отдельно от того, что планируется.

---

## server-site (сайт + платежи)

### Локально
```bash
cd server-site
# поднять Postgres с БД server_site (или docker compose up db)
./mvnw spring-boot:run
# откроется на http://localhost:8080
```
Настройки: `src/main/resources/application.properties`.
Перед продом задать `yookassa.shop-id`, `yookassa.secret-key`, `yookassa.webhook-token`
через переменные окружения, а не в файле (см. SEC-2).

### Docker
```bash
cd server-site
docker compose up --build      # site на :8083, postgres на :5432
```

---

## TgBot (Telegram-бот)

```bash
cd TgBot
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# переменные окружения (не коммитить!):
export BOT_TOKEN="<новый токен от @BotFather>"
export DATABASE_URL="postgresql+asyncpg://tgbot:<pass>@localhost:5432/tgbot"
export ADMINS="123456789"
export DONATION_URL="https://..." JAVA_HOST="mc.astronexis.site" BEDROCK_HOST="bedrock.astronexis.site"

# миграции
alembic upgrade head

# запуск (из каталога src)
cd src && python -m app.main
```

> ⚠️ Сейчас бот читает конфиг и из ENV (`core/config.py`), и из `config.yaml` —
> это рассинхрон (см. BOT-1). До унификации проверь оба файла.

### Docker
```bash
cd TgBot/deploy
docker compose up --build
```

---

## TaipanAuthTg (2FA-плагин)

```bash
cd TaipanAuthTg
./gradlew jar
# результат: build/libs/TaipanAuthTg-0.0.1.jar → в plugins/ сервера
```
Требует AuthMe на сервере. Настройки — `plugins/TaipanAuthTg/config.yml`
(`storage.type`, `telegram.botToken`, `postgres.*`).

---

## AstronexisCore (ядро-плагин)

```bash
cd AstronexisCore
./gradlew jar
# результат: build/libs/AstronexisCore-0.0.1.jar → в plugins/ сервера
```
Настройки — `plugins/AstronexisCore/config.yml` (`modules.menu`, `actionbar.*`).

> На сервере в `plugins/` должен лежать только `AstronexisCore.jar`. Старый
> `TaipanMenuPlugin.jar` удалён из проекта — убери его и с сервера (иначе конфликт `/menu`).

---

---

## Чистка артефактов

В репозитории много мусора (`*Zone.Identifier`, `.idea/`, `.gradle/`, `.venv/`, `libs/*.jar`).
См. задачу `OPS-1` в [TODO.md](TODO.md) — рекомендуется почистить и настроить `.gitignore`.

```bash
# удалить Windows-метки скачивания (их ~1700):
find . -name '*Zone.Identifier' -delete
```
