"""Tests for AI Server liveness and Redis-backed readiness."""

import httpx
import pytest
from redis.exceptions import ConnectionError as RedisConnectionError

from app.config import AppSettings
from app.main import create_app


class StubRedis:
    def __init__(self, *, available: bool) -> None:
        self.available = available

    async def ping(self) -> bool:
        if not self.available:
            raise RedisConnectionError("unavailable")
        return True


async def _get_health(path: str, redis_client: StubRedis) -> httpx.Response:
    app = create_app(AppSettings(gms_key="test-key"))
    app.state.redis_client = redis_client
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        return await client.get(path)


@pytest.mark.asyncio
async def test_liveness_does_not_depend_on_redis() -> None:
    response = await _get_health("/health/live", StubRedis(available=False))

    assert response.status_code == 200
    assert response.json() == {"status": "up"}


@pytest.mark.asyncio
async def test_readiness_is_ready_when_redis_responds() -> None:
    response = await _get_health("/health/ready", StubRedis(available=True))

    assert response.status_code == 200
    assert response.json() == {
        "status": "ready",
        "checks": {"redis": "up"},
    }


@pytest.mark.asyncio
async def test_readiness_is_unavailable_when_redis_is_down() -> None:
    response = await _get_health("/health/ready", StubRedis(available=False))

    assert response.status_code == 503
    assert response.json() == {
        "status": "not_ready",
        "checks": {"redis": "down"},
    }
