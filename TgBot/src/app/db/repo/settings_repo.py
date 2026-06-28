from sqlalchemy.ext.asyncio import AsyncConnection
from sqlalchemy import text

class SettingsRepo:
    async def get(self, con: AsyncConnection, key: str, default: str) -> str:
        row = (await con.execute(text("SELECT value FROM settings WHERE key=:k"), {"k": key})).first()
        return row[0] if row else default

    async def set(self, con: AsyncConnection, key: str, value: str) -> None:
        await con.execute(
            text(
                "INSERT INTO settings(key,value) VALUES (:k,:v) "
                "ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value"
            ),
            {"k": key, "v": value},
        )