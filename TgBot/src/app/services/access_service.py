from dataclasses import dataclass
from telegram import Bot

from app.db.repo.users_repo import UsersRepo
from app.db.repo.settings_repo import SettingsRepo
from app.db.repo.channels_repo import ChannelsRepo
from app.db.repo.bans_repo import BansRepo
from app.db.repo.bindings_repo import BindingsRepo
from app.services.subscriptions_service import SubscriptionsService

@dataclass
class AccessResult:
    ok: bool
    message: str = ""

class AccessService:
    def __init__(self):
        self.users = UsersRepo()
        self.settings = SettingsRepo()
        self.channels = ChannelsRepo()
        self.bans = BansRepo()
        self.bindings = BindingsRepo()
        self.subs = SubscriptionsService()

    async def check(self, con, bot: Bot, tg_id: int) -> AccessResult:
        await self.users.ensure_user(con, tg_id)

        if await self.bans.is_banned(con, tg_id):
            return AccessResult(False, "⛔️ Доступ к боту закрыт (бан).")

        enforce = (await self.settings.get(con, "enforce_subscriptions", "true")).lower() == "true"
        if not enforce:
            return AccessResult(True)

        chans = await self.channels.list(con)
        if not chans:
            # Осознанный fail-open (BOT-9): пустой список каналов = требование
            # подписки не настроено, доступ разрешён.
            return AccessResult(True)

        ok = await self.subs.is_subscribed(bot, tg_id, chans)
        if ok:
            return AccessResult(True)

        if ok is None:
            # Сбой Telegram API (BOT-9): fail-closed для доступа, но привязку НЕ удаляем —
            # временная ошибка не должна быть разрушительной.
            return AccessResult(False, "⚠️ Не удалось проверить подписку. Попробуй ещё раз чуть позже.")

        delete_on_unsub = (await self.settings.get(con, "delete_binding_on_unsub", "true")).lower() == "true"
        if delete_on_unsub:
            await self.bindings.delete_by_tg(con, tg_id)

        return AccessResult(False, "❌ Подпишись на каналы:\n" + "\n".join(chans))