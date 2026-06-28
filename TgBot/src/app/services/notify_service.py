from telegram import Bot
from app.db.repo.settings_repo import SettingsRepo


class NotifyService:
    """
    Единая точка отправки уведомлений админам с тумблером в settings.

    Ключи в settings:
      notify_support = "true"/"false"
      notify_mail    = "true"/"false"
    """
    def __init__(self):
        self.settings = SettingsRepo()

    async def _is_enabled(self, con, key: str, default: str = "true") -> bool:
        v = (await self.settings.get(con, key, default)).lower().strip()
        return v == "true"

    async def admins(self, bot: Bot, cfg, text: str, con=None, toggle_key: str | None = None) -> None:
        """
        con можно не передавать, тогда без тумблера.
        toggle_key если задан — проверяем settings.
        """
        if toggle_key and con is not None:
            if not await self._is_enabled(con, toggle_key, "true"):
                return

        for admin_id in cfg.admins:
            try:
                await bot.send_message(chat_id=admin_id, text=text)
            except Exception:
                # fail-safe: не валим бота из-за 1 админа
                pass