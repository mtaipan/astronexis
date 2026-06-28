from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column
from sqlalchemy import BigInteger, Text, DateTime, ForeignKey, Integer
from sqlalchemy.sql import func


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"

    tg_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    tg_username: Mapped[str | None] = mapped_column(Text, nullable=True)
    tg_first_name: Mapped[str | None] = mapped_column(Text, nullable=True)
    tg_last_name: Mapped[str | None] = mapped_column(Text, nullable=True)

    created_at: Mapped[object] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[object] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class Binding(Base):
    """
    Реальная схема после миграции 0003:
      - platform TEXT NOT NULL
      - nick TEXT NOT NULL
      - UNIQUE (tg_id, platform)
      - UNIQUE (platform, nick)

    В ORM делаем составной PK (platform, nick) — это совпадает по смыслу с уникальностью.
    """
    __tablename__ = "bindings"

    platform: Mapped[str] = mapped_column(Text, primary_key=True)  # "java" | "bedrock"
    nick: Mapped[str] = mapped_column(Text, primary_key=True)

    tg_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.tg_id", ondelete="CASCADE"), nullable=False
    )
    created_at: Mapped[object] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class Ban(Base):
    __tablename__ = "bans"

    tg_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.tg_id", ondelete="CASCADE"), primary_key=True
    )
    reason: Mapped[str] = mapped_column(Text, nullable=False, server_default="")
    created_at: Mapped[object] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class RequiredChannel(Base):
    __tablename__ = "required_channels"

    channel: Mapped[str] = mapped_column(Text, primary_key=True)
    created_at: Mapped[object] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class Setting(Base):
    __tablename__ = "settings"

    key: Mapped[str] = mapped_column(Text, primary_key=True)
    value: Mapped[str] = mapped_column(Text, nullable=False)


class Ticket(Base):
    __tablename__ = "tickets"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    tg_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.tg_id", ondelete="CASCADE"), nullable=False
    )

    category: Mapped[str] = mapped_column(Text, nullable=False)  # support | nickchange
    status: Mapped[str] = mapped_column(Text, nullable=False, server_default="open")  # open|closed
    subject: Mapped[str] = mapped_column(Text, nullable=False, server_default="")

    created_at: Mapped[object] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    closed_at: Mapped[object | None] = mapped_column(DateTime(timezone=True), nullable=True)


class TicketMessage(Base):
    __tablename__ = "ticket_messages"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    ticket_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("tickets.id", ondelete="CASCADE"), nullable=False
    )

    sender_tg_id: Mapped[int | None] = mapped_column(BigInteger, nullable=True)  # None = system
    message: Mapped[str] = mapped_column(Text, nullable=False)

    created_at: Mapped[object] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )