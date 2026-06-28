from sqlalchemy.ext.asyncio import AsyncConnection
from sqlalchemy import text

class TicketsRepo:
    async def create(self, con: AsyncConnection, tg_id: int, category: str, subject: str) -> int:
        row = (await con.execute(
            text(
                "INSERT INTO tickets(tg_id, category, status, subject) "
                "VALUES (:tg, :cat, 'open', :sub) RETURNING id"
            ),
            {"tg": tg_id, "cat": category, "sub": subject},
        )).first()
        return int(row[0])

    async def list_by_user(self, con: AsyncConnection, tg_id: int, limit: int = 20):
        res = await con.execute(
            text(
                "SELECT id, category, status, subject, created_at, closed_at "
                "FROM tickets WHERE tg_id=:tg "
                "ORDER BY id DESC LIMIT :lim"
            ),
            {"tg": tg_id, "lim": limit},
        )
        return res.all()

    async def list_open(self, con: AsyncConnection, limit: int = 50):
        res = await con.execute(
            text(
                "SELECT id, tg_id, category, status, subject, created_at "
                "FROM tickets WHERE status='open' "
                "ORDER BY id ASC LIMIT :lim"
            ),
            {"lim": limit},
        )
        return res.all()

    async def get(self, con: AsyncConnection, ticket_id: int):
        row = (await con.execute(
            text(
                "SELECT id, tg_id, category, status, subject, created_at, closed_at "
                "FROM tickets WHERE id=:id"
            ),
            {"id": ticket_id},
        )).first()
        return row

    async def close(self, con: AsyncConnection, ticket_id: int) -> None:
        await con.execute(
            text(
                "UPDATE tickets SET status='closed', closed_at=NOW() "
                "WHERE id=:id AND status='open'"
            ),
            {"id": ticket_id},
        )