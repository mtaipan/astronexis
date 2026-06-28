from sqlalchemy.ext.asyncio import AsyncConnection
from sqlalchemy import text

class ChannelsRepo:
    async def list(self, con: AsyncConnection) -> list[str]:
        rows = (await con.execute(text("SELECT channel FROM required_channels ORDER BY channel"))).all()
        return [r[0] for r in rows]

    async def add(self, con: AsyncConnection, channel: str) -> None:
        await con.execute(
            text("INSERT INTO required_channels(channel) VALUES (:c) ON CONFLICT DO NOTHING"),
            {"c": channel},
        )

    async def delete(self, con: AsyncConnection, channel: str) -> None:
        await con.execute(text("DELETE FROM required_channels WHERE channel=:c"), {"c": channel})