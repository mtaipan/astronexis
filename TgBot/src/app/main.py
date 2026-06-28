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
from app.bot.handlers.admin import admin_panel


def main():
    cfg = load_config()
    engine = make_engine(cfg.database_url)

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

    # tickets admin
    app.add_handler(CommandHandler("tickets", tickets_cmd))
    app.add_handler(CommandHandler("reply", reply_cmd))
    app.add_handler(CommandHandler("close", close_cmd))

    app.run_polling(allowed_updates=["message", "callback_query"])


if __name__ == "__main__":
    main()