from telegram import Update, InlineKeyboardMarkup, InlineKeyboardButton
from telegram.ext import ContextTypes

from app.bot.middlewares.guard import require_access
from app.bot.texts.ru import RU


@require_access
async def support_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["step"] = "WAIT_SUPPORT_TEXT"
    await update.message.reply_text(RU.SUPPORT_PROMPT, parse_mode="Markdown")


@require_access
async def mytickets_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    tg_id = update.effective_user.id

    tickets_service = context.bot_data["tickets_service"]

    async with context.bot_data["engine"].begin() as con:
        rows = await tickets_service.tickets.list_by_user(con, tg_id, limit=20)

    if not rows:
        await update.message.reply_text("Заявок нет. Нажми «Поддержка» в меню или /support.")
        return

    lines = ["🎟 Твои заявки (последние 20):"]
    for r in rows:
        tid, cat, status, subject, created_at, closed_at = r
        lines.append(f"#{tid} [{cat}] {status} — {subject}")

    lines.append("\nПосмотреть: /ticket <id>")
    await update.message.reply_text("\n".join(lines))


@require_access
async def ticket_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not context.args or not context.args[0].isdigit():
        await update.message.reply_text("Формат: /ticket <id>")
        return

    tid = int(context.args[0])
    tg_id = update.effective_user.id
    tickets_service = context.bot_data["tickets_service"]

    async with context.bot_data["engine"].begin() as con:
        t = await tickets_service.tickets.get(con, tid)
        if not t:
            await update.message.reply_text("Заявка не найдена.")
            return
        if int(t.tg_id) != tg_id and tg_id not in context.bot_data["cfg"].admins:
            await update.message.reply_text("⛔️ Это не твоя заявка.")
            return

        msgs = await tickets_service.msgs.list_last(con, tid, limit=30)

    header = f"Заявка #{t.id} [{t.category}] {t.status}\nТема: {t.subject}\n"
    body_lines = []
    for sid, msg, created_at in msgs:
        if sid == tg_id:
            who = "Ты"
        elif sid is None:
            who = "Система"
        else:
            who = "Админ"
        body_lines.append(f"{who}: {msg}")

    text = header + "\n" + "\n".join(body_lines)

    kb = None
    if t.status == "open" and int(t.tg_id) == tg_id:
        kb = InlineKeyboardMarkup([[
            InlineKeyboardButton(RU.SUPPORT_REPLY_BUTTON, callback_data=f"ticket_reply:{tid}")
        ]])

    await update.message.reply_text(text, reply_markup=kb)