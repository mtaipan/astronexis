from telegram import Update
from telegram.ext import ContextTypes

from app.bot.middlewares.guard import require_access
from app.db.repo.settings_repo import SettingsRepo
from app.services.tickets_service import TicketsService

tickets = TicketsService()
settings_repo = SettingsRepo()


@require_access
async def support_on_text(update: Update, context: ContextTypes.DEFAULT_TYPE):

    step = context.user_data.get("step")
    if step not in ("WAIT_SUPPORT_TEXT", "WAIT_NICKCHANGE_TEXT"):
        return

    tg_id = update.effective_user.id
    text = (update.message.text or "").strip()
    if not text:
        await update.message.reply_text("Напиши текст одним сообщением.")
        return

    category = "support" if step == "WAIT_SUPPORT_TEXT" else "nickchange"
    subject = "Поддержка" if category == "support" else "Смена ника (срочно)"
    first_message = text

    async with context.bot_data["engine"].begin() as con:
        tid = await tickets.create_ticket(con, tg_id, category, subject, first_message)
        notify_admins = (await settings_repo.get(con, "notify_admins_support", "true")).lower() == "true"

    context.user_data["step"] = None

    await update.message.reply_text(
        f"✅ Заявка создана: #{tid}\n"
        "Админы увидят её и ответят здесь.\n\n"
        "Команды:\n"
        "/mytickets — мои заявки\n"
        f"/ticket {tid} — посмотреть"
    )

    # уведомление админов
    if notify_admins:
        u = update.effective_user
        who = f"@{u.username}" if getattr(u, "username", None) else f"tg:{tg_id}"
        msg = (
            "🆘 Новая заявка в поддержку\n"
            f"ID: #{tid}\n"
            f"От: {who}\n"
            f"Категория: {category}\n\n"
            f"{first_message}"
        )
        for admin_id in context.bot_data["cfg"].admins:
            try:
                await context.bot.send_message(admin_id, msg)
            except Exception:
                # fail-safe: не ломаем поддержку из-за невозможности написать админу
                pass