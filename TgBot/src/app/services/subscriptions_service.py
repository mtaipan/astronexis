from telegram import Bot
from telegram.error import TelegramError

_ALLOWED = {"member", "administrator", "creator"}

class SubscriptionsService:
    async def is_subscribed(self, bot: Bot, tg_id: int, channels: list[str]) -> bool:
        for ch in channels:
            try:
                m = await bot.get_chat_member(ch, tg_id)
                if m.status not in _ALLOWED:
                    return False
            except TelegramError:
                return False
        return True