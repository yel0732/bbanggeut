"""Outbound ``conversation-events`` publishing (DIARY-3-T04).

``app/events/envelope.py`` (W04) validates events arriving *from* React. This
is the other direction: it fills in the common Envelope
(REALTIME_EVENT_CONTRACT.md §3) and writes it to the DataChannel.

``eventSeq`` is this connection's **own** send counter. §4 of the contract only
says the field is produced by "React or FastAPI", which leaves open whether the
two directions share one sequence; they cannot, without an atomic counter
spanning both peers, so each direction numbers its own stream and each receiver
checks only what it receives — matching ``ConversationEventGuard``, which
already inspects the inbound direction alone (plan §11-G).

Note what is absent: there is no ``stt.partial`` publisher. whisper-1 is batch
only, so no partial exists to send, and the product decision is a local audio
level meter instead of live subtitles (plan §11-A).
"""

from __future__ import annotations

import logging
import uuid
from collections.abc import Callable
from datetime import datetime, timezone
from typing import Any

from app.webrtc.data_channel import DataChannel

Clock = Callable[[], datetime]
EventIdFactory = Callable[[], str]


def _default_clock() -> datetime:
    # §7: never emit a naive datetime.
    return datetime.now(timezone.utc)


def _default_event_id_factory() -> str:
    return f"evt_{uuid.uuid4().hex}"


class ConversationEventPublisher:
    """Sends Envelope-shaped events to one connection's DataChannel."""

    def __init__(
        self,
        data_channel: DataChannel,
        *,
        conversation_id: str,
        realtime_session_id: str,
        session_epoch: int,
        logger: logging.Logger,
        clock: Clock = _default_clock,
        event_id_factory: EventIdFactory = _default_event_id_factory,
    ) -> None:
        self._data_channel = data_channel
        self._conversation_id = conversation_id
        self._realtime_session_id = realtime_session_id
        self._session_epoch = session_epoch
        self._logger = logger
        self._clock = clock
        self._event_id_factory = event_id_factory
        self._event_seq = 0

    @property
    def last_event_seq(self) -> int:
        return self._event_seq

    def publish(
        self,
        event_type: str,
        payload: dict[str, Any] | None = None,
        *,
        turn_id: str | None = None,
        turn_seq: int | None = None,
        response_id: str | None = None,
    ) -> bool:
        """Send one event. Returns whether it actually went out.

        A closed channel is logged rather than swallowed (§13) — dropping
        events silently would hide a broken UI from us entirely.
        """

        if not self._data_channel.is_open:
            self._logger.warning(
                "conversation event dropped: data channel is closed",
                extra={
                    "conversation_id": self._conversation_id,
                    "realtime_session_id": self._realtime_session_id,
                    "event_type": event_type,
                },
            )
            return False

        self._event_seq += 1
        self._data_channel.send_json(
            {
                "type": event_type,
                "eventId": self._event_id_factory(),
                "conversationId": self._conversation_id,
                "realtimeSessionId": self._realtime_session_id,
                "sessionEpoch": self._session_epoch,
                "turnId": turn_id,
                "turnSeq": turn_seq,
                "responseId": response_id,
                "eventSeq": self._event_seq,
                "occurredAt": self._clock().isoformat(),
                "payload": payload or {},
            }
        )
        return True
