"""Opt-in integration test for the real Redis Lua consumption script.

Unlike the Fake-based tests in test_ticket_verification.py, this exercises
the actual atomicity guarantee of the Lua script against a real Redis
instance. Skipped by default; mirrors the `gms` opt-in test's env-var gate.
"""

import asyncio
import json
import os

import pytest

from app.realtime.redis_client import create_redis_client
from app.realtime.ticket_verification import RedisTicketConsumptionStore, TicketConsumeStatus


def _skip_unless_enabled() -> None:
    if os.getenv("RUN_REDIS_INTEGRATION") != "1":
        pytest.skip("RUN_REDIS_INTEGRATION=1 and a local Redis instance are required")


@pytest.mark.redis
@pytest.mark.asyncio
async def test_atomic_consume_against_real_redis() -> None:
    _skip_unless_enabled()

    redis_client = create_redis_client(
        host=os.getenv("REALTIME_REDIS_HOST", "127.0.0.1"),
        port=int(os.getenv("REALTIME_REDIS_PORT", "6379")),
    )
    store = RedisTicketConsumptionStore(redis_client)
    key = "realtime:ticket:test_atomic_consume"
    record = {
        "conversationId": "conv_test",
        "realtimeSessionId": "rt_test",
        "sessionEpoch": 1,
        "state": "ISSUED",
    }
    try:
        await redis_client.set(key, json.dumps(record), ex=30)

        results = await asyncio.gather(
            store.consume("test_atomic_consume"),
            store.consume("test_atomic_consume"),
        )

        statuses = sorted(result.status for result in results)
        assert statuses == sorted(
            [TicketConsumeStatus.CONSUMED, TicketConsumeStatus.ALREADY_CONSUMED]
        )
    finally:
        await redis_client.delete(key)
        await redis_client.aclose()


@pytest.mark.redis
@pytest.mark.asyncio
async def test_not_found_against_real_redis() -> None:
    _skip_unless_enabled()

    redis_client = create_redis_client(
        host=os.getenv("REALTIME_REDIS_HOST", "127.0.0.1"),
        port=int(os.getenv("REALTIME_REDIS_PORT", "6379")),
    )
    store = RedisTicketConsumptionStore(redis_client)
    try:
        result = await store.consume("never_issued_test_key")
        assert result.status is TicketConsumeStatus.NOT_FOUND
    finally:
        await redis_client.aclose()
