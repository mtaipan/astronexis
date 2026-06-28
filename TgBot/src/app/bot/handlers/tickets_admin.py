from telegram import Update
from telegram.ext import ContextTypes

from app.bot.middlewares.guard import require_access
from app.db.repo.tickets_repo import TicketsRepo
from app.db.repo.ticket_messages_repo import TicketMessagesRepo
from app.db.repo.settings_repo import SettingsRepo

tickets_repo = TicketsRepo()
msgs_repo = TicketMessagesRepo()
settings_repo = SettingsRepo()


def is_admin(update: Update, context: ContextTypes.DEFAULT_TYPE) -> bool:
    return update.effective_user.id in context.bot_data["cfg"].admins


@require_access
async def tickets_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return

    async with context.bot_data["engine"].begin() as con:
        rows = await tickets_repo.list_open(con, limit=50)

    if not rows:
        await update.message.reply_text("Открытых заявок нет.")
        return

    lines = ["Открытые заявки:"]
    for r in rows:
        tid, tg_id, cat, status, subject, created_at = r
        lines.append(f"#{tid} tg:{tg_id} [{cat}] — {subject}")
    lines.append("\nОтвет: /reply <id> <текст>\nЗакрыть: /close <id>")
    await update.message.reply_text("\n".join(lines))


@require_access
async def reply_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if len(context.args) < 2 or not context.args[0].isdigit():
        await update.message.reply_text("Формат: /reply <ticket_id> <текст>")
        return

    tid = int(context.args[0])
    text = " ".join(context.args[1:]).strip()
    admin_id = update.effective_user.id

    async with context.bot_data["engine"].begin() as con:
        t = await tickets_repo.get(con, tid)
        if not t:
            await update.message.reply_text("Заявка не найдена.")
            return

        await msgs_repo.add(con, tid, admin_id, text)
        notify_user = (await settings_repo.get(con, "notify_user_ticket_updates", "true")).lower() == "true"
        user_tg_id = int(t.tg_id)

    await update.message.reply_text("✅ Ответ добавлен.")

    if notify_user:
        try:
            await context.bot.send_message(
                user_tg_id,
                f"💬 Ответ по заявке #{tid}:\n\n{text}\n\n"
                f"Посмотреть историю: /ticket {tid}"
            )
        except Exception:
            pass


@require_access
async def close_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if not context.args or not context.args[0].isdigit():
        await update.message.reply_text("Формат: /close <ticket_id>")
        return

    tid = int(context.args[0])
    admin_id = update.effective_user.id

    async with context.bot_data["engine"].begin() as con:
        t = await tickets_repo.get(con, tid)
        if not t:
            await update.message.reply_text("Заявка не найдена.")
            return

        await tickets_repo.close(con, tid)
        await msgs_repo.add(con, tid, admin_id, "✅ Заявка закрыта админом.")
        notify_user = (await settings_repo.get(con, "notify_user_ticket_updates", "true")).lower() == "true"
        user_tg_id = int(t.tg_id)

    await update.message.reply_text("✅ Закрыто.")

    if notify_user:
        try:
            await context.bot.send_message(
                user_tg_id,
                f"✅ Заявка #{tid} закрыта.\n\n"
                f"История: /ticket {tid}\n"
                "Если проблема осталась — создай новую: /support"
            )
        except Exception:
            pass