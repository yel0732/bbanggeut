"""Tests for the Session Lease Heartbeat loop."""

import asyncio
import json
import logging

import pytest
from redis.exceptions import DataError, RedisError

from app.realtime.session_lease_heartbeat import SessionLeaseHeartbeat


class FakeRedisClient:
    """redis-py의 인자 계약을 그대로 강제하는 fake.

    이전 fake는 ``ex: float``을 그냥 받아서, 실제 redis-py가 float TTL에
    ``DataError``를 던지는 것을 테스트가 놓쳤다 — 모든 하트비트가 조용히
    실패하는 결함이 실 스택에서야 발견됐다. fake는 실물만큼 엄격해야 한다.
    """

    def __init__(self, fail_first_n: int = 0) -> None:
        self.calls: list[tuple[str, str, int]] = []
        self._fail_first_n = fail_first_n
        self._call_count = 0

    async def set(
        self, key: str, value: str, ex: int | None = None, px: int | None = None
    ) -> None:
        # redis-py와 동일한 검증: ex/px는 int(또는 timedelta)만 허용된다.
        if ex is not None and not isinstance(ex, int):
            raise DataError("ex must be datetime.timedelta or int")
        if px is not None and not isinstance(px, int):
            raise DataError("px must be datetime.timedelta or int")
        self._call_count += 1
        if self._call_count <= self._fail_first_n:
            raise RedisError("boom")
        self.calls.append((key, value, px if px is not None else ex))


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

    key, value, ttl = redis_client.calls[0]
    assert key == "realtime:session:conv_1"
    # px 밀리초 int — 설정(0.5초)의 소수점 정밀도를 보존하면서 redis-py 계약을 지킨다.
    assert ttl == 500
    assert isinstance(ttl, int)
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
