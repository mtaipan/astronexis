"""user profile fields for search + indexes

Revision ID: 0004_user_profile_search
Revises: 0003_bindings_platform_and_constraints_and_orders
"""

from alembic import op
import sqlalchemy as sa

revision = "0004_user_profile_search"
down_revision = "0003_bind_platform_credits"
branch_labels = None
depends_on = None

def upgrade():
    op.add_column("users", sa.Column("tg_username", sa.Text(), nullable=True))
    op.add_column("users", sa.Column("tg_first_name", sa.Text(), nullable=True))
    op.add_column("users", sa.Column("tg_last_name", sa.Text(), nullable=True))
    op.add_column("users", sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("NOW()"), nullable=False))

    op.create_index("ix_users_tg_username", "users", ["tg_username"])
    op.create_index("ix_users_tg_first_name", "users", ["tg_first_name"])
    op.create_index("ix_users_tg_last_name", "users", ["tg_last_name"])

def downgrade():
    op.drop_index("ix_users_tg_last_name", table_name="users")
    op.drop_index("ix_users_tg_first_name", table_name="users")
    op.drop_index("ix_users_tg_username", table_name="users")

    op.drop_column("users", "updated_at")
    op.drop_column("users", "tg_last_name")
    op.drop_column("users", "tg_first_name")
    op.drop_column("users", "tg_username")