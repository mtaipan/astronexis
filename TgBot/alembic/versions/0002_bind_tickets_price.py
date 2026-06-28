"""bindings unique tg + tickets + price setting

Revision ID: 0002_bindings_unique_tg_and_tickets_and_price
Revises: 0001_init
Create Date: 2026-02-26
"""

from alembic import op
import sqlalchemy as sa

revision = "0002_bind_tickets_price"
down_revision = "0001_init"
branch_labels = None
depends_on = None

def upgrade():
    # 1) unique tg_id in bindings (1 TG = 1 nick)
    op.create_unique_constraint("uq_bindings_tg_id", "bindings", ["tg_id"])

    # 2) tickets
    op.create_table(
        "tickets",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("tg_id", sa.BigInteger(), sa.ForeignKey("users.tg_id", ondelete="CASCADE"), nullable=False),
        sa.Column("category", sa.Text(), nullable=False),
        sa.Column("status", sa.Text(), nullable=False, server_default="open"),
        sa.Column("subject", sa.Text(), nullable=False, server_default=""),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("NOW()"), nullable=False),
        sa.Column("closed_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_index("ix_tickets_status", "tickets", ["status"])
    op.create_index("ix_tickets_tg_id", "tickets", ["tg_id"])

    op.create_table(
        "ticket_messages",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("ticket_id", sa.Integer(), sa.ForeignKey("tickets.id", ondelete="CASCADE"), nullable=False),
        sa.Column("sender_tg_id", sa.BigInteger(), nullable=True),
        sa.Column("message", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("NOW()"), nullable=False),
    )

    op.create_index("ix_ticket_messages_ticket_id", "ticket_messages", ["ticket_id"])

    # 3) default price setting (если нет)
    # settings table already exists; insert if absent
    op.execute(
        "INSERT INTO settings(key, value) VALUES ('nick_change_price', '199') "
        "ON CONFLICT (key) DO NOTHING"
    )

def downgrade():
    op.drop_index("ix_ticket_messages_ticket_id", table_name="ticket_messages")
    op.drop_table("ticket_messages")

    op.drop_index("ix_tickets_tg_id", table_name="tickets")
    op.drop_index("ix_tickets_status", table_name="tickets")
    op.drop_table("tickets")

    op.drop_constraint("uq_bindings_tg_id", "bindings", type_="unique")