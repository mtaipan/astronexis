"""
Единый роутер текстовых сообщений.

Зачем: раньше в main.py было ДВА MessageHandler на один фильтр (TEXT & ~COMMAND)
в одной группе. python-telegram-bot выполняет только ПЕРВЫЙ подходящий handler
в группе, поэтому support_on_text никогда не срабатывал (тикеты не создавались).

Теперь один handler разводит апдейт по текущему шагу диалога (context.user_data["step"]).
guard вызывается внутри выбранного обработчика ровно один раз.
"""
from telegram import Update
from telegram.ext import ContextTypes

from app.bot.handlers.bind import on_text as bind_on_text
from app.bot.handlers.support_text import support_on_text

# Шаги, которые обслуживает каждый обработчик.
_BIND_STEPS = {"WAIT_NICK"}
_SUPPORT_STEPS = {"WAIT_SUPPORT_TEXT", "WAIT_NICKCHANGE_TEXT"}


async def text_router(update: Update, context: ContextTypes.DEFAULT_TYPE):
    step = context.user_data.get("step")

    if step in _BIND_STEPS:
        await bind_on_text(update, context)
    elif step in _SUPPORT_STEPS:
        await support_on_text(update, context)
    # иначе — свободный текст вне диалога, игнорируем
