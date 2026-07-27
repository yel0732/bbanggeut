"""Common ``conversation-events`` DataChannel Envelope (REALTIME_EVENT_CONTRACT.md §3)
and the full set of §4's inbound discard rules.

W04 implemented the four checks that need no dialogue state (wrong
conversation/session, stale epoch, duplicate eventId). DIARY-3-W03 completed
the remaining three now that turn/response state exists: cancelled
``responseId`` and unknown ``turnId`` are judged through injected hooks (the
guard never imports dialogue objects — the session wires its registry in), and
``eventSeq`` ordering is tracked internally per §4 "이미 처리한 eventSeq 이하".
"""

from __future__ import annotations

from collections.abc import Callable
from datetime import datetime
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.realtime.session_epoch_registry import SessionEpochRegistry

IdentifierPredicate = Callable[[str], bool]


class ConversationEventEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: str
    event_id: str = Field(alias="eventId")
    conversation_id: str = Field(alias="conversationId")
    realtime_session_id: str = Field(alias="realtimeSessionId")
    session_epoch: int = Field(alias="sessionEpoch")
    turn_id: str | None = Field(default=None, alias="turnId")
    turn_seq: int | None = Field(default=None, alias="turnSeq")
    response_id: str | None = Field(default=None, alias="responseId")
    event_seq: int = Field(alias="eventSeq")
    occurred_at: datetime = Field(alias="occurredAt")
    payload: dict[str, Any] = Field(default_factory=dict)

    @field_validator("occurred_at")
    @classmethod
    def _require_timezone_aware(cls, value: datetime) -> datetime:
        # AI_CONVENTIONS.md §7: naive datetime을 만들거나 외부로 보내지 않는다.
        if value.tzinfo is None:
            raise ValueError("occurredAt must be timezone-aware")
        return value


class EventDiscardReason(StrEnum):
    WRONG_CONVERSATION = "wrongConversation"
    WRONG_REALTIME_SESSION = "wrongRealtimeSession"
    STALE_SESSION_EPOCH = "staleSessionEpoch"
    DUPLICATE_EVENT_ID = "duplicateEventId"
    STALE_EVENT_SEQ = "staleEventSeq"
    CANCELLED_RESPONSE = "cancelledResponse"
    UNKNOWN_TURN = "unknownTurn"


class ConversationEventGuard:
    """한 Signaling 연결 동안만 유지되는 §4 폐기 규칙 검사기.

    ``conversation_id``/``realtime_session_id``는 이 연결이 인증에 성공했을 때
    확정된 값이고, 재연결마다 새 인스턴스를 만든다(영속 저장하지 않음, §11-D).

    취소된 responseId·알려진 turnId 판정은 주입 훅으로 받는다 — Guard가
    dialogue 객체를 직접 알면 events → session 역방향 의존이 생긴다. 훅이
    없으면 해당 검사는 건너뛴다(W04까지의 동작과 동일).
    """

    def __init__(
        self,
        conversation_id: str,
        realtime_session_id: str,
        epoch_registry: SessionEpochRegistry,
        *,
        is_response_cancelled: IdentifierPredicate | None = None,
        is_known_turn: IdentifierPredicate | None = None,
    ) -> None:
        self._conversation_id = conversation_id
        self._realtime_session_id = realtime_session_id
        self._epoch_registry = epoch_registry
        self._is_response_cancelled = is_response_cancelled
        self._is_known_turn = is_known_turn
        self._seen_event_ids: set[str] = set()
        self._last_event_seq = 0

    def check(self, envelope: ConversationEventEnvelope) -> EventDiscardReason | None:
        if envelope.conversation_id != self._conversation_id:
            return EventDiscardReason.WRONG_CONVERSATION
        if envelope.realtime_session_id != self._realtime_session_id:
            return EventDiscardReason.WRONG_REALTIME_SESSION
        if not self._epoch_registry.is_current(envelope.conversation_id, envelope.session_epoch):
            return EventDiscardReason.STALE_SESSION_EPOCH
        if envelope.event_id in self._seen_event_ids:
            return EventDiscardReason.DUPLICATE_EVENT_ID
        if envelope.event_seq <= self._last_event_seq:
            # §4: "이미 처리한 eventSeq 이하". 수신 방향(React 송신 수열)만 본다 —
            # 송신 방향은 Publisher가 자기 수열을 따로 관리한다(W01 §11-G).
            return EventDiscardReason.STALE_EVENT_SEQ
        if (
            envelope.response_id is not None
            and self._is_response_cancelled is not None
            and self._is_response_cancelled(envelope.response_id)
        ):
            return EventDiscardReason.CANCELLED_RESPONSE
        if (
            envelope.turn_id is not None
            and self._is_known_turn is not None
            and not self._is_known_turn(envelope.turn_id)
        ):
            return EventDiscardReason.UNKNOWN_TURN
        self._seen_event_ids.add(envelope.event_id)
        self._last_event_seq = envelope.event_seq
        return None
