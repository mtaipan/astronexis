"""Тесты AccessService (BOT-9 / SEC-13).

Фиксируют политику доступа:
- бан → отказ;
- проверка подписок выключена или каналы не заданы → доступ (осознанный fail-open);
- не подписан → отказ (+ удаление привязки, если включено);
- сбой Telegram API → отказ, но привязка НЕ удаляется (временная ошибка не разрушительна).
"""

from unittest.mock import AsyncMock

import pytest

from app.services.access_service import AccessService


@pytest.fixture()
def service():
    s = AccessService()
    s.users = AsyncMock()
    s.bans = AsyncMock()
    s.settings = AsyncMock()
    s.channels = AsyncMock()
    s.bindings = AsyncMock()
    s.subs = AsyncMock()

    # дефолты «обычного» пользователя
    s.bans.is_banned.return_value = False
    s.settings.get.side_effect = lambda con, key, default: {
        "enforce_subscriptions": "true",
        "delete_binding_on_unsub": "true",
    }.get(key, default)
    s.channels.list.return_value = ["@astronexis"]
    return s


async def check(s):
    return await s.check(con=None, bot=None, tg_id=42)


@pytest.mark.asyncio
async def test_banned_user_denied(service):
    service.bans.is_banned.return_value = True
    res = await check(service)
    assert not res.ok


@pytest.mark.asyncio
async def test_no_channels_configured_allows(service):
    service.channels.list.return_value = []
    res = await check(service)
    assert res.ok


@pytest.mark.asyncio
async def test_subscribed_allows(service):
    service.subs.is_subscribed.return_value = True
    res = await check(service)
    assert res.ok


@pytest.mark.asyncio
async def test_not_subscribed_denies_and_deletes_binding(service):
    service.subs.is_subscribed.return_value = False
    res = await check(service)
    assert not res.ok
    service.bindings.delete_by_tg.assert_awaited_once()


@pytest.mark.asyncio
async def test_api_failure_denies_but_keeps_binding(service):
    """Сбой Telegram API не должен удалять привязку игрока."""
    service.subs.is_subscribed.return_value = None
    res = await check(service)
    assert not res.ok
    service.bindings.delete_by_tg.assert_not_awaited()
