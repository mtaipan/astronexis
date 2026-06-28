#!/usr/bin/env bash
set -euo pipefail

export PYTHONPATH=/app/src

# миграции (как Flyway/Liquibase)
alembic upgrade head

# запуск бота
python -m app.main