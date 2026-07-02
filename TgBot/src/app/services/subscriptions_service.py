from telegram import Bot
from telegram.error import TelegramError

_ALLOWED = {"member", "administrator", "creator"}

class SubscriptionsService:
    async def is_subscribed(self, bot: Bot, tg_id: int, channels: list[str]) -> bool | None:
        """Проверяет подписку на все каналы.

        Возвращает точный ответ (True/False) либо None, если проверить не удалось
        (сбой Telegram API). Различие важно (BOT-9): «точно не подписан» может влечь
        удаление привязки, а «не удалось проверить» — нет.
        """
        for ch in channels:
            try:
                m = await bot.get_chat_member(ch, tg_id)
                if m.status not in _ALLOWED:
                    return False
            except TelegramError:
                return None
        return True
