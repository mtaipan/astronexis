from sqlalchemy.ext.asyncio import AsyncConnection
from sqlalchemy import text

class UsersRepo:
    async def ensure_user(self, con: AsyncConnection, tg_id: int, username: str | None = None,
                          first_name: str | None = None, last_name: str | None = None) -> None:
        await con.execute(
            text(
                "INSERT INTO users(tg_id, tg_username, tg_first_name, tg_last_name, updated_at) "
                "VALUES (:id, :u, :f, :l, NOW()) "
                "ON CONFLICT (tg_id) DO UPDATE SET "
                "tg_username=COALESCE(EXCLUDED.tg_username, users.tg_username), "
                "tg_first_name=COALESCE(EXCLUDED.tg_first_name, users.tg_first_name), "
                "tg_last_name=COALESCE(EXCLUDED.tg_last_name, users.tg_last_name), "
                "updated_at=NOW()"
            ),
            {"id": tg_id, "u": username, "f": first_name, "l": last_name},
        )

    async def find_by_username(self, con: AsyncConnection, username: str, limit: int = 20):
        u = username.lstrip("@")
        res = await con.execute(
            text(
                "SELECT tg_id, tg_username, tg_first_name, tg_last_name "
                "FROM users WHERE tg_username ILIKE :u "
                "ORDER BY updated_at DESC LIMIT :lim"
            ),
            {"u": u, "lim": limit},
        )
        return res.all()

    async def find_by_name(self, con: AsyncConnection, q: str, limit: int = 20):
        pat = f"%{q.strip()}%"
        res = await con.execute(
            text(
                "SELECT tg_id, tg_username, tg_first_name, tg_last_name "
                "FROM users "
                "WHERE COALESCE(tg_first_name,'') ILIKE :p "
                "   OR COALESCE(tg_last_name,'') ILIKE :p "
                "   OR (COALESCE(tg_first_name,'') || ' ' || COALESCE(tg_last_name,'')) ILIKE :p "
                "ORDER BY updated_at DESC LIMIT :lim"
            ),
            {"p": pat, "lim": limit},
        )
        return res.all()