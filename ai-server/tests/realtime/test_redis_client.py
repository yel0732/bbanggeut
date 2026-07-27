"""Tests for the async Redis client factory."""

from app.realtime.redis_client import create_redis_client


def test_creates_client_with_given_host_and_port() -> None:
    client = create_redis_client(host="redis.internal", port=16379)

    connection_kwargs = client.connection_pool.connection_kwargs
    assert connection_kwargs["host"] == "redis.internal"
    assert connection_kwargs["port"] == 16379


def test_decodes_responses_as_text() -> None:
    client = create_redis_client(host="127.0.0.1", port=6379)

    assert client.connection_pool.connection_kwargs["decode_responses"] is True
