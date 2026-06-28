from alembic import op
import sqlalchemy as sa

revision = "0001_init"
down_revision = None
branch_labels = None
depends_on = None

def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("tg_id", sa.BigInteger(), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
    )

    op.create_table(
        "bindings",
        sa.Column("nick", sa.Text(), primary_key=True),
        sa.Column("tg_id", sa.BigInteger(), sa.ForeignKey("users.tg_id", ondelete="CASCADE"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
    )

    op.create_table(
        "bans",
        sa.Column("tg_id", sa.BigInteger(), sa.ForeignKey("users.tg_id", ondelete="CASCADE"), primary_key=True),
        sa.Column("reason", sa.Text(), nullable=False, server_default=""),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
    )

    op.create_table(
        "required_channels",
        sa.Column("channel", sa.Text(), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
    )

    op.create_table(
        "settings",
        sa.Column("key", sa.Text(), primary_key=True),
        sa.Column("value", sa.Text(), nullable=False),
    )

    # дефолты
    op.execute("INSERT INTO settings(key, value) VALUES ('enforce_subscriptions','true') ON CONFLICT (key) DO NOTHING;")
    op.execute("INSERT INTO settings(key, value) VALUES ('delete_binding_on_unsub','true') ON CONFLICT (key) DO NOTHING;")

def downgrade() -> None:
    op.drop_table("settings")
    op.drop_table("required_channels")
    op.drop_table("bans")
    op.drop_table("bindings")
    op.drop_table("users")