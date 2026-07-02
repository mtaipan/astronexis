from telegram.ext import (
    Application,
    CommandHandler,
    MessageHandler,
    CallbackQueryHandler,
    filters,
)

from app.core.config import load_config
from app.db.engine import make_engine

from app.bot.handlers.start import start
from app.bot.handlers.menu import menu_on_callback

# bind flow
from app.bot.handlers.bind import bind_cmd, on_bind_callback

# support/tickets
from app.bot.handlers.support import support_cmd, mytickets_cmd, ticket_cmd
from app.bot.handlers.tickets_admin import tickets_cmd, reply_cmd, close_cmd

# единый роутер текстовых сообщений (см. text_router.py)
from app.bot.handlers.text_router import text_router

# admin
from app.bot.handlers.admin import (
    admin_panel,
    channels_cmd,
    channel_add,
    channel_del,
    subs_cmd,
    ban_cmd,
    unban_cmd,
    price_get_cmd,
    price_set_cmd,
    bind_get_cmd,
    bind_set_cmd,
    bind_del_cmd,
    bind_del_nick_cmd,
    find_nick_cmd,
    find_tg_cmd,
    find_name_cmd,
)

# Все админ-команды из меню /admin — одним списком, чтобы регистрация циклом
# не давала «объявлен, но не подключён» (BOT-8).
ADMIN_COMMANDS = [
    ("channels", channels_cmd),
    ("channel_add", channel_add),
    ("channel_del", channel_del),
    ("subs", subs_cmd),
    ("ban", ban_cmd),
    ("unban", unban_cmd),
    ("price_get", price_get_cmd),
    ("price_set", price_set_cmd),
    ("bind_get", bind_get_cmd),
    ("bind_set", bind_set_cmd),
    ("bind_del", bind_del_cmd),
    ("bind_del_nick", bind_del_nick_cmd),
    ("find_nick", find_nick_cmd),
    ("find_tg", find_tg_cmd),
    ("find_name", find_name_cmd),
]


def build_application(cfg, engine) -> Application:
    """Собирает Application со всеми хендлерами (без запуска polling) — тестируемо."""
    app = Application.builder().token(cfg.bot_token).build()

    app.bot_data["cfg"] = cfg
    app.bot_data["engine"] = engine

    # --- user ---
    app.add_handler(CommandHandler("start", start))

    # menu callbacks
    app.add_handler(CallbackQueryHandler(menu_on_callback, pattern=r"^menu_"))

    # bind
    app.add_handler(CommandHandler("bind", bind_cmd))
    app.add_handler(CallbackQueryHandler(on_bind_callback, pattern=r"^bind_"))

    # Единый текстовый роутер: разводит сообщение по шагу диалога (bind / support).
    # Раньше тут было два MessageHandler на один фильтр — срабатывал только первый (BOT-2).
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, text_router))

    app.add_handler(CommandHandler("support", support_cmd))
    app.add_handler(CommandHandler("mytickets", mytickets_cmd))
    app.add_handler(CommandHandler("ticket", ticket_cmd))

    # --- admin ---
    app.add_handler(CommandHandler("admin", admin_panel))
    for name, handler in ADMIN_COMMANDS:
        app.add_handler(CommandHandler(name, handler))

    # tickets admin
    app.add_handler(CommandHandler("tickets", tickets_cmd))
    app.add_handler(CommandHandler("reply", reply_cmd))
    app.add_handler(CommandHandler("close", close_cmd))

    return app


def main():
    cfg = load_config()
    engine = make_engine(cfg.database_url)

    app = build_application(cfg, engine)

    app.run_polling(allowed_updates=["message", "callback_query"])


if __name__ == "__main__":
    main()