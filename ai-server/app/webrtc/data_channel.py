"""DataChannel abstraction for the ``conversation-events`` channel (label
``conversation-events``, ``ordered: true`` — React creates it, FastAPI only
receives it via ``PeerConnection.on_data_channel_received()``, see
jira/plan/DIARY-2-W03.md §1 aiortc datachannel-event findings, reused here).

Only JSON object messages are supported — matching the Envelope contract
(REALTIME_EVENT_CONTRACT.md §3). Malformed (non-JSON, non-object) messages are
silently dropped at this transport layer; Envelope-shape validation and
discard-rule checks happen one layer up (app/events/envelope.py,
app/signaling/connection.py) per jira/plan/DIARY-2-W04.md §11-E (no client
notification for DataChannel-level rejections).
"""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable
from typing import Any, Protocol

MessageCallback = Callable[[dict[str, Any]], Awaitable[None]]


class DataChannel(Protocol):
    def send_json(self, payload: dict[str, Any]) -> None: ...
    def on_message(self, callback: MessageCallback) -> None:
        """Register the (single) callback invoked for each valid JSON object message."""
        ...

    @property
    def is_open(self) -> bool: ...
    def close(self) -> None: ...


class FakeDataChannel:
    """Test double: records sent payloads and lets tests trigger inbound messages."""

    def __init__(self) -> None:
        self.sent: list[dict[str, Any]] = []
        self.closed = False
        self._callback: MessageCallback | None = None

    def send_json(self, payload: dict[str, Any]) -> None:
        self.sent.append(payload)

    def on_message(self, callback: MessageCallback) -> None:
        self._callback = callback

    @property
    def is_open(self) -> bool:
        return not self.closed

    def close(self) -> None:
        self.closed = True

    async def simulate_message(self, payload: dict[str, Any]) -> None:
        if self._callback is not None:
            await self._callback(payload)


class AiortcDataChannel:
    """Wraps a real ``aiortc.RTCDataChannel`` (only ever received, never created here)."""

    def __init__(self, channel: Any) -> None:
        self._channel = channel
        self._callback: MessageCallback | None = None
        channel.on("message", self._handle_message)

    def send_json(self, payload: dict[str, Any]) -> None:
        self._channel.send(json.dumps(payload))

    def on_message(self, callback: MessageCallback) -> None:
        self._callback = callback

    @property
    def is_open(self) -> bool:
        return self._channel.readyState == "open"

    def close(self) -> None:
        self._channel.close()

    async def _handle_message(self, message: str | bytes) -> None:
        if self._callback is None or not isinstance(message, str):
            return
        try:
            payload = json.loads(message)
        except json.JSONDecodeError:
            return
        if isinstance(payload, dict):
            await self._callback(payload)
