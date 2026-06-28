from sqlalchemy.ext.asyncio import AsyncConnection
from sqlalchemy import text

class TicketMessagesRepo:
    async def add(self, con: AsyncConnection, ticket_id: int, sender_tg_id: int | None, message: str) -> None:
        await con.execute(
            text(
                "INSERT INTO ticket_messages(ticket_id, sender_tg_id, message) "
                "VALUES (:tid, :sid, :msg)"
            ),
            {"tid": ticket_id, "sid": sender_tg_id, "msg": message},
        )

    async def list_last(self, con: AsyncConnection, ticket_id: int, limit: int = 30):
        res = await con.execute(
            text(
                "SELECT sender_tg_id, message, created_at "
                "FROM ticket_messages WHERE ticket_id=:tid "
                "ORDER BY id DESC LIMIT :lim"
            ),
            {"tid": ticket_id, "lim": limit},
        )
        rows = res.all()
        return list(reversed(rows))