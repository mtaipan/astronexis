from dataclasses import dataclass


@dataclass(frozen=True)
class RU:
    BRAND = "ASTRONEXIS"

    START_TITLE = (
        "👋 Привет! Это официальный бот сервера ASTRONEXIS.\n\n"
        "Выбери пункт меню ниже."
    )

    MENU_HINT = "Меню:"

    HOW_JOIN = (
        "🎮 *Как зайти на сервер*\n\n"
        "*Java:*\n"
        "• Адрес: `{java}`\n\n"
        "*Bedrock:*\n"
        "• Адрес: `{bedrock}`\n"
        "• Порт: `19132`\n\n"
        "Если не пускает — проверь, что ты вводишь адрес без пробелов."
    )

    FAQ = (
        "❓ *FAQ*\n\n"
        "• *Нужно ли что-то скачивать?* — Нет.\n"
        "• *Можно с Bedrock?* — Да, через Geyser/Floodgate.\n"
        "• *Не заходит / ошибка входа?* — Напиши в поддержку.\n"
        "• *Как привязать аккаунт?* — /bind.\n\n"
        "Если вопрос не из FAQ — жми «Поддержка»."
    )

    SUPPORT_PROMPT = (
        "🆘 *Поддержка*\n\n"
        "Опиши проблему *одним сообщением*.\n"
        "Я создам заявку и уведомлю админов.\n\n"
        "Пример:\n"
        "• Ник (java/bedrock)\n"
        "• Что случилось\n"
        "• Когда\n"
        "• Скрин (если есть)"
    )

    SUPPORT_CREATED = (
        "✅ Заявка создана: *#{tid}*\n\n"
        "Посмотреть: /ticket {tid}\n"
        "Список: /mytickets"
    )

    SUPPORT_REPLY_BUTTON = "✍️ Написать в заявку"
    BACK = "⬅️ Назад"

    DONATE = (
        "💖 *Поддержать проект*\n\n"
        "Спасибо! Донат помогает развивать сервер.\n"
        "Жми кнопку ниже 👇"
    )

    ADMIN_NEW_TICKET = (
        "🆘 *Новая заявка* #{tid}\n"
        "tg: `{tg_id}`\n"
        "Категория: `{cat}`\n"
        "Тема: {sub}\n\n"
        "Сообщение:\n{msg}\n\n"
        "Ответить: /reply {tid} <текст>\n"
        "Закрыть: /close {tid}"
    )

    ADMIN_NEW_TICKET_MSG = (
        "💬 *Новое сообщение в заявке* #{tid}\n"
        "От: `{tg_id}`\n\n"
        "{msg}\n\n"
        "Ответить: /reply {tid} <текст>\n"
        "Закрыть: /close {tid}"
    )

    USER_ADMIN_REPLY = (
        "✅ *Ответ от поддержки* по заявке #{tid}:\n\n"
        "{msg}\n\n"
        "Посмотреть: /ticket {tid}"
    )

    USER_TICKET_CLOSED = (
        "✅ Заявка *#{tid}* закрыта админом.\n"
        "Если проблема осталась — создай новую: «Поддержка»."
    )

    NOTIFY_HELP = (
        "🔔 Уведомления\n\n"
        "Формат:\n"
        "/notify support on|off\n"
        "/notify mail on|off\n"
        "/notify_status"
    )

    NOTIFY_STATUS = (
        "🔔 Статус уведомлений:\n"
        "support = {support}\n"
        "mail    = {mail}\n"
    )