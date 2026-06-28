from sqlalchemy.ext.asyncio import AsyncConnection
from sqlalchemy import text


class BindingsRepo:
    async def get_tg_by_platform_nick(self, con: AsyncConnection, platform: str, nick: str) -> int | None:
        row = (
            await con.execute(
                text("SELECT tg_id FROM bindings WHERE platform=:p AND nick=:n"),
                {"p": platform, "n": nick},
            )
        ).first()
        return int(row[0]) if row else None

    async def get_nick_by_tg_platform(self, con: AsyncConnection, tg_id: int, platform: str) -> str | None:
        row = (
            await con.execute(
                text("SELECT nick FROM bindings WHERE tg_id=:id AND platform=:p"),
                {"id": tg_id, "p": platform},
            )
        ).first()
        return row[0] if row else None

    async def insert(self, con: AsyncConnection, platform: str, nick: str, tg_id: int) -> None:
        await con.execute(
            text("INSERT INTO bindings(platform, nick, tg_id) VALUES (:p,:n,:id)"),
            {"p": platform, "n": nick, "id": tg_id},
        )

    async def delete_by_tg(self, con: AsyncConnection, tg_id: int) -> None:
        """
        Удалить ВСЕ привязки пользователя (и java, и bedrock).
        Нужно для сценария: отписался от required_channels -> wipe bindings.
        """
        await con.execute(
            text("DELETE FROM bindings WHERE tg_id=:id"),
            {"id": tg_id},
        )

    async def delete_by_tg_platform(self, con: AsyncConnection, tg_id: int, platform: str) -> None:
        await con.execute(
            text("DELETE FROM bindings WHERE tg_id=:id AND platform=:p"),
            {"id": tg_id, "p": platform},
        )

    async def delete_by_platform_nick(self, con: AsyncConnection, platform: str, nick: str) -> None:
        await con.execute(
            text("DELETE FROM bindings WHERE platform=:p AND nick=:n"),
            {"p": platform, "n": nick},
        )

    async def get_all_by_tg(self, con: AsyncConnection, tg_id: int):
        res = await con.execute(
            text("SELECT platform, nick, created_at FROM bindings WHERE tg_id=:id ORDER BY platform"),
            {"id": tg_id},
        )
        return res.all()

    async def find_by_platform_nick_like(self, con: AsyncConnection, platform: str, nick_like: str, limit: int = 20):
        pat = f"%{nick_like}%"
        res = await con.execute(
            text(
                "SELECT tg_id, platform, nick, created_at "
                "FROM bindings "
                "WHERE platform=:p AND nick ILIKE :pat "
                "ORDER BY created_at DESC LIMIT :lim"
            ),
            {"p": platform, "pat": pat, "lim": limit},
        )
        return res.all()