"""Tests for the Session Lease Heartbeat loop."""

import asyncio
import json
import logging

import pytest
from redis.exceptions import RedisError

from app.realtime.session_lease_heartbeat import SessionLeaseHeartbeat


class FakeRedisClient:
    def __init__(self, fail_first_n: int = 0) -> None:
        self.calls: list[tuple[str, str, float]] = []
        self._fail_first_n = fail_first_n
        self._call_count = 0

    async def set(self, key: str, value: str, ex: float) -> None:
        self._call_count += 1
        if self._call_count <= self._fail_first_n:
            raise RedisError("boom")
        self.calls.append((key, value, ex))


async def _wait_until(condition, timeout: float = 1.0) -> None:
    async def poll() -> None:
        while not condition():
            await asyncio.sleep(0.001)

    await asyncio.wait_for(poll(), timeout=timeout)


def _heartbeat(redis_client, interval: float = 0.01, ttl: float = 0.5) -> SessionLeaseHeartbeat:
    return SessionLeaseHeartbeat(
        redis_client, interval, ttl, logging.getLogger("test.realtime.heartbeat")
    )


@pytest.mark.asyncio
async def test_writes_heartbeat_immediately_and_periodically() -> None:
    redis_client = FakeRedisClient()
    heartbeat = _heartbeat(redis_client)

    await heartbeat.start("conv_1", "rt_1", 1)
    await _wait_until(lambda: len(redis_client.calls) >= 3)
    await heartbeat.stop()

    key, value, ex = redis_client.calls[0]
    assert key == "realtime:session:conv_1"
    assert ex == 0.5
    record = json.loads(value)
    assert record["realtimeSessionId"] == "rt_1"
    assert record["sessionEpoch"] == 1
    assert record["state"] == "ACTIVE"
    assert isinstance(record["lastHeartbeatAt"], int)


@pytest.mark.asyncio
async def test_stop_halts_further_writes() -> None:
    redis_client = FakeRedisClient()
    heartbeat = _heartbeat(redis_client)

    await heartbeat.start("conv_1", "rt_1", 1)
    await _wait_until(lambda: len(redis_client.calls) >= 2)
    await heartbeat.stop()
    count_after_stop = len(redis_client.calls)
    await asyncio.sleep(0.05)

    assert len(redis_client.calls) == count_after_stop
    assert not heartbeat.is_running


@pytest.mark.asyncio
async def test_starting_twice_raises() -> None:
    heartbeat = _heartbeat(FakeRedisClient())
    await heartbeat.start("conv_1", "rt_1", 1)

    with pytest.raises(RuntimeError):
        await heartbeat.start("conv_1", "rt_1", 1)

    await heartbeat.stop()


@pytest.mark.asyncio
async def test_stop_without_start_is_noop() -> None:
    heartbeat = _heartbeat(FakeRedisClient())

    await heartbeat.stop()

    assert not heartbeat.is_running


@pytest.mark.asyncio
async def test_transient_redis_failure_does_not_stop_the_loop() -> None:
    redis_client = FakeRedisClient(fail_first_n=2)
    heartbeat = _heartbeat(redis_client)

    await heartbeat.start("conv_1", "rt_1", 1)
    await _wait_until(lambda: len(redis_client.calls) >= 2)
    await heartbeat.stop()

    assert len(redis_client.calls) >= 2


def test_rejects_non_positive_heartbeat_interval() -> None:
    with pytest.raises(ValueError):
        SessionLeaseHeartbeat(
            FakeRedisClient(), 0, 15.0, logging.getLogger("test")
        )


def test_rejects_non_positive_lease_ttl() -> None:
    with pytest.raises(ValueError):
        SessionLeaseHeartbeat(
            FakeRedisClient(), 5.0, 0, logging.getLogger("test")
        )
