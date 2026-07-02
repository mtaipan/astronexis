# Astronexis

Экосистема Minecraft-сервера **Astronexis** (Java + Bedrock): игровое ядро-плагин,
2FA-авторизация через Telegram, веб-сайт с приёмом донатов/VIP через ЮKassa и
Telegram-бот поддержки и привязок аккаунтов.

> Документация и план работ лежат в каталоге [`docs/`](docs/).
> Начни с [docs/README.md](docs/README.md) — это оглавление.

## Состав репозитория

| Каталог | Стек | Назначение |
|---|---|---|
| [`AstronexisCore/`](AstronexisCore) | Java 21, Paper API 1.21.1 | Модульное ядро серверного плагина (меню, action bar, компас-хаб) |
| [`TaipanAuthTg/`](TaipanAuthTg) | Java, Spigot API 1.21, AuthMe, Postgres | Двухфакторная авторизация игроков через Telegram |
| [`server-site/`](server-site) | Spring Boot 3.5, Thymeleaf, Postgres, Flyway | Сайт проекта + донаты и VIP через ЮKassa |
| [`TgBot/`](TgBot) | Python 3.12, python-telegram-bot 21.6, SQLAlchemy async, Alembic | Telegram-бот: привязки, тикеты, проверка подписок, админка |

## Карта документации

- [docs/OVERVIEW.md](docs/OVERVIEW.md) — что это за проект, как модули связаны, глоссарий.
- [docs/STATUS.md](docs/STATUS.md) — честная оценка состояния (что проверено, что нет).
- [docs/ROADMAP.md](docs/ROADMAP.md) — путь к «отличному» проекту (3 фазы).
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — архитектура и потоки данных.
- [docs/DECISIONS.md](docs/DECISIONS.md) — архитектурные решения (ADR) и их обоснование.
- [docs/SECURITY.md](docs/SECURITY.md) — найденные уязвимости и риски.
- [docs/TODO.md](docs/TODO.md) — **полный пошаговый план исправлений** (чек-листы по приоритетам).
- [docs/SETUP.md](docs/SETUP.md) — как запустить каждый модуль локально и в Docker.
- [docs/modules/](docs/modules) — подробная документация по каждому модулю.

## Быстрый старт (для разработки)

См. [docs/SETUP.md](docs/SETUP.md). Кратко:

```bash
# Сайт
cd server-site && ./mvnw spring-boot:run

# Telegram-бот
cd TgBot && python -m venv .venv && source .venv/bin/activate \
  && pip install -r requirements.txt && alembic upgrade head \
  && python -m app.main   # из каталога src

# Плагины Minecraft
cd AstronexisCore && ./gradlew jar
cd TaipanAuthTg   && ./gradlew jar
```

> ⚠️ **Не деплоить как есть.** Независимое ревью нашло работающие дыры, которых не было в
> прошлом списке: обход 2FA переждав TTL кода (SEC-8), мёртвая половина админки бота (SEC-9),
> гонка двойной выдачи VIP (SEC-10), блокирующий JDBC в тик-потоке (SEC-11). Плюс ноль тестов.
> Чинить сверху вниз по [docs/TODO.md](docs/TODO.md) («Этап 0-bis»). Полный разбор —
> [docs/SECURITY.md](docs/SECURITY.md) и [docs/STATUS.md](docs/STATUS.md).
> Секреты вынесены в ENV, но bot-token и пароли БД лучше перевыпустить (SEC-2).
