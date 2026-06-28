# Модуль: AstronexisCore

Модульное ядро серверного плагина: внутриигровое меню, компас-навигатор (хаб), action bar,
мультиплатформенный рендер (сундук-меню для Java, формы Floodgate для Bedrock).

- **Стек:** Java 21, Paper API 1.21.1 (`compileOnly`).
- **Сборка:** Gradle → `AstronexisCore-0.0.1.jar`.
- **Команда:** `/menu` (право `astronexis.menu`, по умолчанию всем).

## Архитектура (модульный «монолит-плагин»)

```
AstronexisCorePlugin (JavaPlugin)
├── ServiceRegistry         // общие сервисы
│   ├── MessageService      // i18n из конфигов
│   └── PlatformService     // Java/Bedrock через Floodgate
└── ModuleManager           // реестр CoreModule, enableAll/disableAll
    └── MenuModule (id="menu", флаг modules.menu)
        ├── LastLocationStore     // запоминание последней локации (persist)
        ├── MenuService           // логика страниц меню (MenuPage, MenuHolder)
        ├── ActionBarService      // периодический action bar (планировщик)
        ├── ui/JavaMenuRenderer   // инвентарь-сундук
        ├── ui/BedrockMenuRenderer// Floodgate-формы
        └── listener/             // MenuListener, CompassListener,
                                  //   CompassProtectListener, HubCompassGiver, ActionBarListener
```

Паттерн модуля:

```java
public interface CoreModule {
    String id();
    void enable();
    void disable();
}
```

Включение/выключение модулей флагами в `config.yml` (`modules.menu`, `actionbar.enabled`, …).

## Структура каталогов

```
AstronexisCore/src/main/java/dev/taipan/astronexis/core/
├── AstronexisCorePlugin.java
├── bootstrap/{ServiceRegistry, ModuleManager}.java
├── module/
│   ├── CoreModule.java
│   └── menu/
│       ├── MenuModule.java (+ вложенный ActionBarService)
│       ├── MenuService.java, MenuPage.java, MenuHolder.java, MenuCommand.java
│       ├── ui/{JavaMenuRenderer, BedrockMenuRenderer}.java
│       ├── listener/{MenuListener, CompassListener, CompassProtectListener,
│       │             HubCompassGiver, ActionBarListener}.java
│       └── store/LastLocationStore.java
└── shared/
    ├── i18n/MessageService.java
    ├── platform/PlatformService.java
    └── text/Texts.java
```

## Конфигурация (ключи `config.yml`)

- `modules.menu` (bool) — включить меню-модуль.
- `actionbar.enabled` / `enabledForJava` / `enabledForBedrock` (bool).
- `actionbar.intervalTicks` (long, дефолт 40), `actionbar.text` (с `%player% %world% %online%`),
  `actionbar.worlds` (список миров-фильтр).

## 🟢 Сильные стороны

- **Зрелая модульная архитектура**: `ServiceRegistry` + `ModuleManager` + `CoreModule`,
  явные `enable/disable`, безопасный `disableAll` (try/catch на модуль).
- Разделение рендера по платформам (Java/Bedrock) и общий `PlatformService`.
- i18n через `MessageService`, конфигурируемый action bar с плейсхолдерами и фильтром миров.
- Чистая остановка планировщика и сохранение `LastLocationStore` в `disable`.

## 🔴🟠🟡 Слабые места / TODO

| ID | Severity | Проблема |
|---|---|---|
| [ARCH-2](../TODO.md) | ✅ | Дубль `TaipanMenuPlugin` удалён; вся функциональность меню здесь. |
| [CORE-1](../TODO.md) | 🟡 | `ActionBarService` вложен в `MenuModule` как static-класс — вынести в отдельный сервис/модуль |
| [CORE-2](../TODO.md) | 🟡 | Цвета/текст через ручную замену `&`→`§` — рассмотреть Adventure API (Paper) |
| [CORE-3](../TODO.md) | 🟡 | Нет тестов; нет валидации значений конфига (отрицательные интервалы частично защищены) |
| [CORE-4](../TODO.md) | 🟡 | Только один модуль (`menu`); задокументировать, как добавлять новые модули (шаблон CoreModule) |

## Как собрать

`cd AstronexisCore && ./gradlew jar` → `build/libs/AstronexisCore-0.0.1.jar`.
Положить в `plugins/` сервера Paper 1.21.1.
