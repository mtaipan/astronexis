from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import ContextTypes

from app.bot.middlewares.guard import require_access
from app.db.repo.settings_repo import SettingsRepo

settings_repo = SettingsRepo()


def _main_kb() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup([
        [InlineKeyboardButton("🔗 Привязать ник", callback_data="menu_bind")],
        [InlineKeyboardButton("🆘 Поддержка", callback_data="menu_support")],
        [InlineKeyboardButton("💛 Поддержать проект", callback_data="menu_donate")],
        [InlineKeyboardButton("⚙️ Уведомления", callback_data="menu_notifs")],
        [InlineKeyboardButton("ℹ️ FAQ", callback_data="menu_faq")],
    ])


def _back_kb() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup([[InlineKeyboardButton("⬅️ Назад", callback_data="menu_home")]])


def _notifs_kb(admins_on: bool, user_on: bool) -> InlineKeyboardMarkup:
    a = "✅" if admins_on else "❌"
    u = "✅" if user_on else "❌"
    return InlineKeyboardMarkup([
        [InlineKeyboardButton(f"{a} Админам о новых тикетах", callback_data="menu_toggle_admins")],
        [InlineKeyboardButton(f"{u} Пользователю о ответах", callback_data="menu_toggle_user")],
        [InlineKeyboardButton("⬅️ Назад", callback_data="menu_home")],
    ])


@require_access
async def menu_on_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):

    q = update.callback_query
    await q.answer()

    data = (q.data or "").strip()
    cfg = context.bot_data["cfg"]

    if data == "menu_home":
        context.user_data["step"] = None
        await q.edit_message_text(
            "ASTRONEXIS — меню\n\nВыбери действие:",
            reply_markup=_main_kb()
        )
        return

    if data == "menu_bind":
        # просто подсказываем команду, чтобы не городить второй UI
        await q.edit_message_text(
            "🔗 Привязка ника\n\nНажми /bind и следуй шагам.",
            reply_markup=_back_kb()
        )
        return

    if data == "menu_support":
        context.user_data["step"] = "WAIT_SUPPORT_TEXT"
        await q.edit_message_text(
            "🆘 Поддержка\n\n"
            "Опиши проблему одним сообщением.\n"
            "Я создам заявку и уведомлю админов.\n\n"
            "Пример:\n"
            "• Ник (java/bedrock)\n"
            "• Что случилось\n"
            "• Когда\n"
            "• Скрин (если есть)",
            reply_markup=_back_kb()
        )
        return

    if data == "menu_donate":
        url = getattr(cfg, "donation_url", None) or "https://www.donationalerts.com/"
        await q.edit_message_text(
            "💛 Поддержать проект\n\n"
            "Спасибо! Вот ссылка:\n"
            f"{url}\n\n"
            "После доната можешь написать в поддержку, если нужно.",
            reply_markup=_back_kb()
        )
        return

    if data == "menu_faq":
        java_host = getattr(cfg, "java_host", "mc.astronexis.site")
        bed_host = getattr(cfg, "bedrock_host", "bedrock.astronexis.site")
        await q.edit_message_text(
            "ℹ️ FAQ\n\n"
            f"Java сервер: {java_host}\n"
            f"Bedrock сервер: {bed_host} (стандартный порт)\n\n"
            "Команды:\n"
            "/bind — привязать ник\n"
            "/support — поддержка\n"
            "/mytickets — мои заявки\n\n"
            "Если что-то сломалось — пиши в поддержку.",
            reply_markup=_back_kb()
        )
        return

    if data == "menu_notifs":
        async with context.bot_data["engine"].begin() as con:
            admins_on = (await settings_repo.get(con, "notify_admins_support", "true")).lower() == "true"
            user_on = (await settings_repo.get(con, "notify_user_ticket_updates", "true")).lower() == "true"

        await q.edit_message_text(
            "⚙️ Уведомления\n\n"
            "Настройки применяются сразу.",
            reply_markup=_notifs_kb(admins_on, user_on)
        )
        return

    if data in ("menu_toggle_admins", "menu_toggle_user"):
        async with context.bot_data["engine"].begin() as con:
            admins_on = (await settings_repo.get(con, "notify_admins_support", "true")).lower() == "true"
            user_on = (await settings_repo.get(con, "notify_user_ticket_updates", "true")).lower() == "true"

            if data == "menu_toggle_admins":
                admins_on = not admins_on
                await settings_repo.set(con, "notify_admins_support", "true" if admins_on else "false")

            if data == "menu_toggle_user":
                user_on = not user_on
                await settings_repo.set(con, "notify_user_ticket_updates", "true" if user_on else "false")

        await q.edit_message_text(
            "⚙️ Уведомления\n\n"
            "Настройки применяются сразу.",
            reply_markup=_notifs_kb(admins_on, user_on)
        )
        return

    # неизвестное
    await q.edit_message_text("❓ Неизвестное действие.", reply_markup=_back_kb())