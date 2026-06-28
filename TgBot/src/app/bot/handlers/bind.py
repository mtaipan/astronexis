import re
import time
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import ContextTypes

from app.bot.middlewares.guard import require_access
from app.services.bindings_service import BindingsService, BindError

bindings = BindingsService()

NICK_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")
PENDING_TTL_SECONDS = 60

@require_access
async def bind_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):

    context.user_data.clear()

    kb = InlineKeyboardMarkup([[
        InlineKeyboardButton("Java", callback_data="bind_platform_java"),
        InlineKeyboardButton("Bedrock", callback_data="bind_platform_bedrock"),
    ]])

    await update.message.reply_text(
        "Выбери платформу аккаунта Minecraft:",
        reply_markup=kb
    )

@require_access
async def on_bind_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    q = update.callback_query
    await q.answer()
    data = q.data or ""

    # 1) выбор платформы
    if data in ("bind_platform_java", "bind_platform_bedrock"):
        platform = "java" if data.endswith("_java") else "bedrock"
        context.user_data["platform"] = platform
        context.user_data["step"] = "WAIT_NICK"

        await q.edit_message_text(
            "Напиши ник одним сообщением.\n"
            "Формат: 3-16 символов, A-Z, 0-9, _\n\n"
            f"Платформа: {platform.upper()}\n"
            "Важно: после привязки смена ника — только через оплату или поддержку."
        )
        return

    # 2) confirm/cancel
    if data in ("bind_confirm", "bind_cancel"):
        step = context.user_data.get("step")
        if step != "WAIT_CONFIRM":
            await q.edit_message_text("⏳ Сессия не активна. Напиши /bind заново.")
            return

        pending_until = float(context.user_data.get("pending_until") or 0)
        if time.time() > pending_until:
            context.user_data.clear()
            await q.edit_message_text("⏳ Время вышло. Напиши /bind заново.")
            return

        if data == "bind_cancel":
            context.user_data.clear()
            await q.edit_message_text("❌ Отменено. /bind чтобы начать заново.")
            return

        # confirm
        tg_id = update.effective_user.id
        platform = context.user_data.get("platform")
        nick = context.user_data.get("pending_nick")
        if not platform or not nick:
            context.user_data.clear()
            await q.edit_message_text("⏳ Данные потеряны. /bind заново.")
            return

        async with context.bot_data["engine"].begin() as con:
            try:
                await bindings.bind_strict(con, tg_id, platform, nick)
            except BindError as e:
                if e.code == "TG_ALREADY_BOUND_PLATFORM":
                    await q.edit_message_text(
                        f"✅ У тебя уже привязан {platform.upper()} ник: `{e.details}`\n\n"
                        "Смена ника: /nickchange (платно) или /support (бесплатно, но ждать).",
                        parse_mode="Markdown"
                    )
                    return
                if e.code == "NICK_TAKEN":
                    await q.edit_message_text(
                        "❌ Этот ник уже привязан к другому Telegram.\n\n"
                        "Если это твой ник — /support"
                    )
                    return
                await q.edit_message_text("❌ Ошибка. /support")
                return

        context.user_data.clear()
        await q.edit_message_text(
            f"✅ Привязано.\nПлатформа: {platform.upper()}\n"
            "Теперь заходи на сервер: после /login придёт код для /tgcode."
        )
        return

@require_access
async def on_text(update: Update, context: ContextTypes.DEFAULT_TYPE):

    if context.user_data.get("step") != "WAIT_NICK":
        return

    platform = context.user_data.get("platform")
    if platform not in ("java", "bedrock"):
        context.user_data.clear()
        await update.message.reply_text("⏳ Платформа не выбрана. Напиши /bind заново.")
        return

    nick = (update.message.text or "").strip()
    base = nick[4:] if nick.lower().startswith("bed_") else nick

    if not NICK_RE.match(base):
        await update.message.reply_text("❌ Ник неверный. Пример: Nick_Name123")
        return

    # сохраняем pending на подтверждение
    context.user_data["step"] = "WAIT_CONFIRM"
    context.user_data["pending_nick"] = nick
    context.user_data["pending_until"] = time.time() + PENDING_TTL_SECONDS

    preview = bindings.normalize_nick(platform, nick)

    kb = InlineKeyboardMarkup([[
        InlineKeyboardButton("✅ Подтвердить", callback_data="bind_confirm"),
        InlineKeyboardButton("❌ Отмена", callback_data="bind_cancel"),
    ]])

    await update.message.reply_text(
        "Проверь ник внимательно.\n\n"
        f"Платформа: {platform.upper()}\n"
        f"Будет сохранено как: `{preview}`\n\n"
        f"Время на подтверждение: {PENDING_TTL_SECONDS} сек.",
        reply_markup=kb,
        parse_mode="Markdown"
    )