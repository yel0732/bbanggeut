"""Periodic Redis TTL refresh for ``realtime:session:{conversationId}``.

FastAPI is the sole Heartbeat sender (Spring only writes the initial Lease at
issuance; see docs/diary/DECISIONS.md ADR-009). This class has no dependency
on a real PeerConnection — W03/W04 call start()/stop() at the points where a
PeerConnection actually becomes active/inactive.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time

from redis.exceptions import RedisError

_SESSION_KEY_PREFIX = "realtime:session:"
_STATE_ACTIVE = "ACTIVE"


class SessionLeaseHeartbeat:
    def __init__(
        self,
        redis_client,
        heartbeat_interval_seconds: float,
        lease_ttl_seconds: float,
        logger: logging.Logger,
    ) -> None:
        if heartbeat_interval_seconds <= 0:
            raise ValueError("heartbeat_interval_seconds must be positive")
        if lease_ttl_seconds <= 0:
            raise ValueError("lease_ttl_seconds must be positive")
        self._redis = redis_client
        self._heartbeat_interval_seconds = heartbeat_interval_seconds
        self._lease_ttl_seconds = lease_ttl_seconds
        self._logger = logger
        self._task: asyncio.Task | None = None

    @property
    def is_running(self) -> bool:
        return self._task is not None

    async def start(
        self, conversation_id: str, realtime_session_id: str, session_epoch: int
    ) -> None:
        if self._task is not None:
            raise RuntimeError("heartbeat already started")
        self._task = asyncio.create_task(
            self._run(conversation_id, realtime_session_id, session_epoch)
        )

    async def stop(self) -> None:
        task = self._task
        if task is None:
            return
        self._task = None
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass

    async def _run(
        self, conversation_id: str, realtime_session_id: str, session_epoch: int
    ) -> None:
        try:
            while True:
                await self._beat(conversation_id, realtime_session_id, session_epoch)
                await asyncio.sleep(self._heartbeat_interval_seconds)
        except asyncio.CancelledError:
            raise

    async def _beat(
        self, conversation_id: str, realtime_session_id: str, session_epoch: int
    ) -> None:
        record = {
            "realtimeSessionId": realtime_session_id,
            "sessionEpoch": session_epoch,
            "state": _STATE_ACTIVE,
            "lastHeartbeatAt": int(time.time() * 1000),
        }
        key = f"{_SESSION_KEY_PREFIX}{conversation_id}"
        try:
            await self._redis.set(key, json.dumps(record), ex=self._lease_ttl_seconds)
        except RedisError:
            self._logger.warning(
                "realtime session heartbeat write failed",
                extra={"conversation_id": conversation_id},
            )
