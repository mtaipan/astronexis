from sqlalchemy.ext.asyncio import create_async_engine, AsyncEngine

def make_engine(database_url: str) -> AsyncEngine:
    return create_async_engine(database_url, pool_pre_ping=True)