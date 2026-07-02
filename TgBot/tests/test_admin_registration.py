"""Smoke-тест регистрации команд (BOT-8 / SEC-9).

Половина админки была объявлена в admin.py, но не подключена в main.py — команды
молча не отвечали. Тест ловит рецидив: каждая команда, которую бот сам рекламирует
в меню /admin, должна быть зарегистрирована в Application.
"""

import inspect
import re
from types import SimpleNamespace

import pytest
from telegram.ext import CommandHandler

from app.bot.handlers import admin
from app.main import ADMIN_COMMANDS, build_application


def _registered_commands(app) -> set[str]:
    cmds: set[str] = set()
    for group in app.handlers.values():
        for h in group:
            if isinstance(h, CommandHandler):
                cmds |= set(h.commands)
    return cmds


@pytest.fixture()
def app():
    cfg = SimpleNamespace(bot_token="123:TEST", admins=[1], database_url="postgresql://x/x")
    return build_application(cfg, engine=None)


def test_every_command_from_admin_menu_is_registered(app):
    """Все /команды из текста меню /admin реально подключены."""
    menu_text = inspect.getsource(admin.admin_panel)
    advertised = set(re.findall(r"/([a-z_]+)(?:\b)", menu_text))
    advertised -= {"admin"}  # сама панель регистрируется отдельно

    registered = _registered_commands(app)
    missing = advertised - registered
    assert not missing, f"Команды из меню /admin не зарегистрированы: {sorted(missing)}"


def test_admin_commands_list_matches_handlers_in_admin_module(app):
    """Каждый async-хендлер из admin.py (кроме панели) есть в ADMIN_COMMANDS."""
    handlers = {
        fn
        for name, fn in vars(admin).items()
        if inspect.iscoroutinefunction(fn) and fn.__module__ == admin.__name__ and name != "admin_panel"
    }
    wired = {fn for _, fn in ADMIN_COMMANDS}
    unwired = {fn.__name__ for fn in handlers - wired}
    assert not unwired, f"Хендлеры admin.py вне ADMIN_COMMANDS: {sorted(unwired)}"


def test_core_user_commands_registered(app):
    registered = _registered_commands(app)
    assert {"start", "bind", "support", "mytickets", "ticket", "admin"} <= registered
