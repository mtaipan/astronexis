from functools import wraps

from sqlalchemy import text

from app.services.access_service import AccessService

access = AccessService()


def require_access(handler):
    """
    Декоратор для хендлеров: прогоняет guard (бан/подписки/профиль) и пускает дальше,
    только если доступ разрешён. Заменяет ручной `if not await guard(...)` в каждом
    хендлере — нельзя забыть проверку (BOT-3).
    """
    @wraps(handler)
    async def wrapper(update, context):
        if not await guard(update, context):
            return
        return await handler(update, context)

    return wrapper

async def _upsert_user_profile(con, tg_id: int, username: str | None, first_name: str | None, last_name: str | None):
    """
    Пишем/обновляем профиль пользователя в users.
    Требует миграции с колонками:
      users.tg_username, users.tg_first_name, users.tg_last_name, users.updated_at
    Сделано fail-safe: если колонок еще нет — просто молча пропускаем.
    """
    try:
        await con.execute(
            text(
                "INSERT INTO users(tg_id, tg_username, tg_first_name, tg_last_name, updated_at) "
                "VALUES (:id, :u, :f, :l, NOW()) "
                "ON CONFLICT (tg_id) DO UPDATE SET "
                "tg_username=COALESCE(EXCLUDED.tg_username, users.tg_username), "
                "tg_first_name=COALESCE(EXCLUDED.tg_first_name, users.tg_first_name), "
                "tg_last_name=COALESCE(EXCLUDED.tg_last_name, users.tg_last_name), "
                "updated_at=NOW()"
            ),
            {"id": tg_id, "u": username, "f": first_name, "l": last_name},
        )
    except Exception:
        # Колонок/таблицы может не быть (если миграции не применены).
        # Не ломаем весь бот из-за профиля.
        return

async def guard(update, context) -> bool:
    tg_id = update.effective_user.id

    async with context.bot_data["engine"].begin() as con:
        # 1) твоя текущая бизнес-проверка (бан/подписки/и т.п.)
        res = await access.check(con, context.bot, tg_id)
        if not res.ok:
            await update.effective_message.reply_text(res.message)
            return False

        # 2) сохраняем профиль для поиска админом
        u = update.effective_user
        await _upsert_user_profile(
            con,
            tg_id=tg_id,
            username=getattr(u, "username", None),
            first_name=getattr(u, "first_name", None),
            last_name=getattr(u, "last_name", None),
        )

    return True