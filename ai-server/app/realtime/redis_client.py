"""Async Redis client factory for Realtime Ticket/Lease access."""

from redis.asyncio import Redis


def create_redis_client(host: str, port: int) -> Redis:
    """Build a lazily-connecting async Redis client.

    Construction never opens a connection; redis-py connects on first use.
    """

    return Redis(host=host, port=port, decode_responses=True)
