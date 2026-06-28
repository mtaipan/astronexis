from dataclasses import dataclass
from app.db.repo.users_repo import UsersRepo
from app.db.repo.bindings_repo import BindingsRepo

@dataclass
class BindError(Exception):
    code: str
    details: str | None = None

class BindingsService:
    def __init__(self):
        self.users = UsersRepo()
        self.bindings = BindingsRepo()

    def normalize_nick(self, platform: str, nick: str) -> str:
        nick = nick.strip()
        if platform == "bedrock":
            # в БД всегда bed_<nick_without_prefix>
            base = nick
            if base.lower().startswith("bed_"):
                base = base[4:]
            return "bed_" + base
        return nick

    async def bind_strict(self, con, tg_id: int, platform: str, nick: str) -> None:
        """
        Правила:
        - 1 TG может иметь максимум 1 ник на platform=java и 1 ник на platform=bedrock
        - 1 nick в рамках platform может принадлежать только одному TG
        - обычный пользователь НЕ может перезаписать чужой ник
        - обычный пользователь НЕ может менять ник (смена — через оплату или саппорт)
        """
        await self.users.ensure_user(con, tg_id)

        norm = self.normalize_nick(platform, nick)

        existing = await self.bindings.get_nick_by_tg_platform(con, tg_id, platform)
        if existing:
            raise BindError("TG_ALREADY_BOUND_PLATFORM", existing)

        owner = await self.bindings.get_tg_by_platform_nick(con, platform, norm)
        if owner and owner != tg_id:
            raise BindError("NICK_TAKEN")

        await self.bindings.insert(con, platform, norm, tg_id)

    async def admin_set(self, con, tg_id: int, platform: str, nick: str) -> None:
        await self.users.ensure_user(con, tg_id)
        norm = self.normalize_nick(platform, nick)

        # снять старую связь tg_id/platform
        await self.bindings.delete_by_tg_platform(con, tg_id, platform)
        # снять, если ник занят кем-то
        await self.bindings.delete_by_platform_nick(con, platform, norm)

        await self.bindings.insert(con, platform, norm, tg_id)

    async def admin_unbind(self, con, tg_id: int, platform: str) -> None:
        await self.bindings.delete_by_tg_platform(con, tg_id, platform)