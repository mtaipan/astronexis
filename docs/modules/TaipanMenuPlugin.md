# Модуль: TaipanMenuPlugin — УДАЛЁН

Этот модуль удалён из репозитория (ARCH-2). Он полностью заменён модулем `menu` в
[AstronexisCore](AstronexisCore.md).

## Что было перенесено

Паритет проверен перед удалением — всё реализовано в `AstronexisCore`:

| Было в TaipanMenuPlugin | Стало в AstronexisCore |
|---|---|
| Страницы меню MAIN/HELP/HELP_PRIVATES/HELP_COMMANDS | `module.menu.MenuPage` + рендереры |
| Телепорты survival(last/spawn)/hub | `MenuService.teleportTo*` |
| Компас-хаб (PDC `menu_compass`), защита компаса | `MenuService.createHubCompass`, `CompassProtectListener` |
| Cooldown кликов | `MenuService.allowClick` |
| Тексты справки (privates/commands) | i18n: `help.privates` / `help.commands` в `messages_ru.yml` |
| — | **Дополнительно:** Bedrock-рендер, action bar, i18n, модульная обёртка |

## Деплой

Убедись, что на сервере в `plugins/` лежит только `AstronexisCore.jar`, а старый
`TaipanMenuPlugin.jar` удалён (иначе будет конфликт команды `/menu`).
