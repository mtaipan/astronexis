"""bindings platform + constraints + paid nick change orders

Revision ID: 0003_bindings_platform_and_constraints_and_orders
Revises: 0002_bindings_unique_tg_and_tickets_and_price
Create Date: 2026-02-26
"""

from alembic import op
import sqlalchemy as sa

revision = "0003_bind_platform_credits"
down_revision = "0002_bind_tickets_price"
branch_labels = None
depends_on = None

def upgrade():
    # 1) drop old unique tg_id (from 0002)
    op.drop_constraint("uq_bindings_tg_id", "bindings", type_="unique")

    # 2) add platform column
    op.add_column("bindings", sa.Column("platform", sa.Text(), nullable=True))

    # заполнение по умолчанию: всё что уже есть считаем java
    op.execute("UPDATE bindings SET platform='java' WHERE platform IS NULL")

    # сделать NOT NULL
    op.alter_column("bindings", "platform", nullable=False)

    # 3) новые уникальные ограничения
    op.create_unique_constraint("uq_bindings_tg_platform", "bindings", ["tg_id", "platform"])
    op.create_unique_constraint("uq_bindings_platform_nick", "bindings", ["platform", "nick"])

    # 4) таблица платных смен ника (простая модель "есть оплаченный слот")
    op.create_table(
        "nick_change_credits",
        sa.Column("tg_id", sa.BigInteger(), sa.ForeignKey("users.tg_id", ondelete="CASCADE"), primary_key=True),
        sa.Column("platform", sa.Text(), nullable=False),
        sa.Column("credits", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("NOW()"), nullable=False),
    )
    op.create_unique_constraint("uq_nick_change_credits_tg_platform", "nick_change_credits", ["tg_id", "platform"])

def downgrade():
    op.drop_constraint("uq_nick_change_credits_tg_platform", "nick_change_credits", type_="unique")
    op.drop_table("nick_change_credits")

    op.drop_constraint("uq_bindings_platform_nick", "bindings", type_="unique")
    op.drop_constraint("uq_bindings_tg_platform", "bindings", type_="unique")

    op.drop_column("bindings", "platform")

    # вернуть старую уникальность tg_id (как было в 0002)
    op.create_unique_constraint("uq_bindings_tg_id", "bindings", ["tg_id"])