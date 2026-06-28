from sqlalchemy.ext.asyncio import AsyncConnection
from sqlalchemy import text

class BansRepo:
    async def is_banned(self, con: AsyncConnection, tg_id: int) -> bool:
        row = (await con.execute(text("SELECT 1 FROM bans WHERE tg_id=:id"), {"id": tg_id})).first()
        return row is not None

    async def ban(self, con: AsyncConnection, tg_id: int, reason: str) -> None:
        await con.execute(
            text(
                "INSERT INTO bans(tg_id, reason) VALUES (:id,:r) "
                "ON CONFLICT (tg_id) DO UPDATE SET reason=EXCLUDED.reason"
            ),
            {"id": tg_id, "r": reason},
        )
        await con.execute(text("DELETE FROM bindings WHERE tg_id=:id"), {"id": tg_id})

    async def unban(self, con: AsyncConnection, tg_id: int) -> None:
        await con.execute(text("DELETE FROM bans WHERE tg_id=:id"), {"id": tg_id})