from telegram import Update
from telegram.ext import ContextTypes

from app.bot.middlewares.guard import require_access

from app.db.repo.channels_repo import ChannelsRepo
from app.db.repo.settings_repo import SettingsRepo
from app.db.repo.bans_repo import BansRepo
from app.db.repo.users_repo import UsersRepo
from app.db.repo.bindings_repo import BindingsRepo

from app.services.bindings_service import BindingsService

channels_repo = ChannelsRepo()
settings_repo = SettingsRepo()
bans_repo = BansRepo()
users_repo = UsersRepo()
bindings_repo = BindingsRepo()
bindings_service = BindingsService()


def is_admin(update: Update, context: ContextTypes.DEFAULT_TYPE) -> bool:
    return update.effective_user.id in context.bot_data["cfg"].admins


def _normalize_platform(p: str) -> str | None:
    p = (p or "").lower().strip()
    if p in ("java", "bedrock"):
        return p
    return None


def _normalize_bed_nick(nick: str) -> str:
    nick = nick.strip()
    if nick.lower().startswith("bed_"):
        return nick
    return "bed_" + nick


# -------------------- PANEL --------------------

@require_access
async def admin_panel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return

    async with context.bot_data["engine"].begin() as con:
        chans = await channels_repo.list(con)
        enforce = await settings_repo.get(con, "enforce_subscriptions", "true")
        price = await settings_repo.get(con, "nick_change_price", "199")

    await update.message.reply_text(
        "🛠 Админка\n"
        f"Проверка подписок: {enforce}\n"
        f"Цена смены ника: {price}\n"
        f"Каналы: {', '.join(chans) if chans else '(пусто)'}\n\n"
        "Команды:\n"
        "/channels\n"
        "/channel_add @channel\n"
        "/channel_del @channel\n"
        "/subs on|off\n"
        "/ban <tg_id> <reason>\n"
        "/unban <tg_id>\n\n"
        "Bindings:\n"
        "/bind_get <tg_id>\n"
        "/bind_set <tg_id> <java|bedrock> <nick>\n"
        "/bind_del <tg_id> <java|bedrock>\n"
        "/bind_del_nick <java|bedrock> <nick>\n\n"
        "Search:\n"
        "/find_nick <java|bedrock> <nick_part>\n"
        "/find_tg @username\n"
        "/find_name <text>\n\n"
        "Price:\n"
        "/price_get\n"
        "/price_set <число>\n\n"
        "Tickets:\n"
        "/tickets\n"
        "/reply <id> <text>\n"
        "/close <id>\n"
    )


# -------------------- CHANNELS --------------------

@require_access
async def channels_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return

    async with context.bot_data["engine"].begin() as con:
        chans = await channels_repo.list(con)

    await update.message.reply_text("Каналы:\n" + ("\n".join(chans) if chans else "(пусто)"))


@require_access
async def channel_add(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if not context.args:
        await update.message.reply_text("Формат: /channel_add @channel")
        return

    ch = context.args[0].strip()
    async with context.bot_data["engine"].begin() as con:
        await channels_repo.add(con, ch)

    await update.message.reply_text("✅ Добавлено.")


@require_access
async def channel_del(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if not context.args:
        await update.message.reply_text("Формат: /channel_del @channel")
        return

    ch = context.args[0].strip()
    async with context.bot_data["engine"].begin() as con:
        await channels_repo.delete(con, ch)

    await update.message.reply_text("✅ Удалено.")


# -------------------- SUBS SWITCH --------------------

@require_access
async def subs_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if not context.args or context.args[0] not in ("on", "off"):
        await update.message.reply_text("Формат: /subs on|off")
        return

    val = "true" if context.args[0] == "on" else "false"
    async with context.bot_data["engine"].begin() as con:
        await settings_repo.set(con, "enforce_subscriptions", val)

    await update.message.reply_text(f"✅ enforce_subscriptions={val}")


# -------------------- BANS --------------------

@require_access
async def ban_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if len(context.args) < 1 or not context.args[0].isdigit():
        await update.message.reply_text("Формат: /ban <tg_id> <reason>")
        return

    tg_id = int(context.args[0])
    reason = " ".join(context.args[1:]).strip() if len(context.args) > 1 else "ban"

    async with context.bot_data["engine"].begin() as con:
        await users_repo.ensure_user(con, tg_id)
        await bans_repo.ban(con, tg_id, reason)

        # отвязка: снимаем и java, и bedrock (если репо поддерживает delete_by_tg — если нет, просто пропускаем)
        try:
            await bindings_repo.delete_by_tg_platform(con, tg_id, "java")
            await bindings_repo.delete_by_tg_platform(con, tg_id, "bedrock")
        except Exception:
            pass

    await update.message.reply_text("✅ Забанен (и попытался отвязать ники).")


@require_access
async def unban_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if not context.args or not context.args[0].isdigit():
        await update.message.reply_text("Формат: /unban <tg_id>")
        return

    tg_id = int(context.args[0])
    async with context.bot_data["engine"].begin() as con:
        await bans_repo.unban(con, tg_id)

    await update.message.reply_text("✅ Разбанен.")


# -------------------- PRICE --------------------

@require_access
async def price_get_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return

    async with context.bot_data["engine"].begin() as con:
        price = await settings_repo.get(con, "nick_change_price", "199")

    await update.message.reply_text(f"Цена смены ника: {price}")


@require_access
async def price_set_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if not context.args or not context.args[0].isdigit():
        await update.message.reply_text("Формат: /price_set <число>")
        return

    price = context.args[0]
    async with context.bot_data["engine"].begin() as con:
        await settings_repo.set(con, "nick_change_price", price)

    await update.message.reply_text(f"✅ Цена смены ника = {price}")


# -------------------- BINDINGS ADMIN --------------------

@require_access
async def bind_get_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if not context.args or not context.args[0].isdigit():
        await update.message.reply_text("Формат: /bind_get <tg_id>")
        return

    tg_id = int(context.args[0])
    async with context.bot_data["engine"].begin() as con:
        rows = await bindings_repo.get_all_by_tg(con, tg_id)

    if not rows:
        await update.message.reply_text("Привязок нет.")
        return

    lines = [f"tg:{tg_id} привязки:"]
    for platform, nick, created_at in rows:
        lines.append(f"- {platform}: {nick}")
    await update.message.reply_text("\n".join(lines))


@require_access
async def bind_set_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if len(context.args) < 3 or not context.args[0].isdigit():
        await update.message.reply_text("Формат: /bind_set <tg_id> <java|bedrock> <nick>")
        return

    tg_id = int(context.args[0])
    platform = _normalize_platform(context.args[1])
    nick = context.args[2].strip()

    if not platform:
        await update.message.reply_text("Платформа: java или bedrock")
        return

    async with context.bot_data["engine"].begin() as con:
        await bindings_service.admin_set(con, tg_id, platform, nick)

    await update.message.reply_text("✅ Принудительно привязано.")


@require_access
async def bind_del_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if len(context.args) < 2 or not context.args[0].isdigit():
        await update.message.reply_text("Формат: /bind_del <tg_id> <java|bedrock>")
        return

    tg_id = int(context.args[0])
    platform = _normalize_platform(context.args[1])
    if not platform:
        await update.message.reply_text("Платформа: java или bedrock")
        return

    async with context.bot_data["engine"].begin() as con:
        await bindings_repo.delete_by_tg_platform(con, tg_id, platform)

    await update.message.reply_text("✅ Отвязано.")


@require_access
async def bind_del_nick_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if len(context.args) < 2:
        await update.message.reply_text("Формат: /bind_del_nick <java|bedrock> <nick>")
        return

    platform = _normalize_platform(context.args[0])
    nick = context.args[1].strip()

    if not platform:
        await update.message.reply_text("Платформа: java или bedrock")
        return

    if platform == "bedrock":
        nick = _normalize_bed_nick(nick)

    async with context.bot_data["engine"].begin() as con:
        await bindings_repo.delete_by_platform_nick(con, platform, nick)

    await update.message.reply_text("✅ Удалено по нику.")


# -------------------- SEARCH --------------------

@require_access
async def find_nick_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if len(context.args) < 2:
        await update.message.reply_text("Формат: /find_nick <java|bedrock> <nick_part>")
        return

    platform = _normalize_platform(context.args[0])
    q = " ".join(context.args[1:]).strip()

    if not platform:
        await update.message.reply_text("Платформа: java или bedrock")
        return

    like = q
    if platform == "bedrock" and not q.lower().startswith("bed_"):
        like = "bed_" + q

    async with context.bot_data["engine"].begin() as con:
        rows = await bindings_repo.find_by_platform_nick_like(con, platform, like, limit=20)

    if not rows:
        await update.message.reply_text("Ничего не найдено.")
        return

    lines = ["Найдено:"]
    for tg_id, p, nick, created_at in rows:
        lines.append(f"tg:{tg_id} {p}:{nick}")
    await update.message.reply_text("\n".join(lines))


@require_access
async def find_tg_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if not context.args:
        await update.message.reply_text("Формат: /find_tg @username")
        return

    username = context.args[0].strip()
    async with context.bot_data["engine"].begin() as con:
        rows = await users_repo.find_by_username(con, username, limit=20)

    if not rows:
        await update.message.reply_text("Ничего не найдено.")
        return

    lines = ["Найдено:"]
    for tg_id, u, f, l in rows:
        name = ((f or "") + " " + (l or "")).strip()
        lines.append(f"tg:{tg_id} @{u or '-'} {name}")
    await update.message.reply_text("\n".join(lines))


@require_access
async def find_name_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update, context):
        return
    if not context.args:
        await update.message.reply_text("Формат: /find_name <text>")
        return

    q = " ".join(context.args).strip()
    async with context.bot_data["engine"].begin() as con:
        rows = await users_repo.find_by_name(con, q, limit=20)

    if not rows:
        await update.message.reply_text("Ничего не найдено.")
        return

    lines = ["Найдено:"]
    for tg_id, u, f, l in rows:
        name = ((f or "") + " " + (l or "")).strip()
        lines.append(f"tg:{tg_id} @{u or '-'} {name}")
    await update.message.reply_text("\n".join(lines))