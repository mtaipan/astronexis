from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import ContextTypes

from app.bot.middlewares.guard import require_access
from app.db.repo.settings_repo import SettingsRepo

settings = SettingsRepo()


def _main_kb() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup([
        [InlineKeyboardButton("🔗 Привязать ник", callback_data="menu_bind")],
        [InlineKeyboardButton("🆘 Поддержка", callback_data="menu_support")],
        [InlineKeyboardButton("💛 Поддержать проект", callback_data="menu_donate")],
        [InlineKeyboardButton("⚙️ Уведомления", callback_data="menu_notifs")],
        [InlineKeyboardButton("ℹ️ FAQ", callback_data="menu_faq")],
    ])


@require_access
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):

    async with context.bot_data["engine"].begin() as con:
        price = await settings.get(con, "nick_change_price", "199")

    await update.message.reply_text(
        "Привет! Это бот сервера ASTRONEXIS.\n\n"
        "Быстрый старт:\n"
        "1) Нажми «Привязать ник»\n"
        "2) Зайди на сервер и следуй инструкциям\n\n"
        "Команды (если нужно):\n"
        "/bind — привязать ник\n"
        "/support — поддержка\n"
        f"/nickchange — срочная смена ника (пока через тикет, цена-ориентир: {price})\n"
        "/mytickets — мои заявки\n",
        reply_markup=_main_kb()
    )