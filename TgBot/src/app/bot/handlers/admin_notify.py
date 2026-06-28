from telegram import Update
from telegram.ext import ContextTypes

from app.bot.middlewares.guard import require_access
from app.bot.texts.ru import RU
from app.db.repo.settings_repo import SettingsRepo


settings = SettingsRepo()


def is_admin(update: Update, context: ContextTypes.DEFAULT_TYPE) -> bool:
    return update.effective_user.id in context.bot_data["cfg"].admins


def _normalize_key(k: str) -> str | None:
    k = (k or "").strip().lower()
    if k == "support":
        return "notify_support"
    if k == "mail":
        return "notify_mail"
    return None


@require_access
async def notify_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return

    if len(context.args) != 2:
        await update.message.reply_text(RU.NOTIFY_HELP)
        return

    key = _normalize_key(context.args[0])
    val = context.args[1].strip().lower()

    if not key or val not in ("on", "off"):
        await update.message.reply_text(RU.NOTIFY_HELP)
        return

    v = "true" if val == "on" else "false"
    async with context.bot_data["engine"].begin() as con:
        await settings.set(con, key, v)

    await update.message.reply_text(f"✅ {key}={v}")


@require_access
async def notify_status_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return

    async with context.bot_data["engine"].begin() as con:
        s = (await settings.get(con, "notify_support", "true")).lower()
        m = (await settings.get(con, "notify_mail", "true")).lower()

    await update.message.reply_text(
        RU.NOTIFY_STATUS.format(support=s, mail=m)
    )