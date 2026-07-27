"""Common ``conversation-events`` DataChannel Envelope (REALTIME_EVENT_CONTRACT.md §3)
and the subset of §4's discard rules that can actually be judged in W04.

``responseId`` cancellation, ``eventSeq`` ordering, and ``turnId`` existence
require real turn/response state that only exists once DIARY-3's Dialogue
Manager is wired in — this module deliberately does not check them yet
(jira/plan/DIARY-2-W04.md §11-B). DIARY-3 extends ``ConversationEventGuard``
with those checks rather than replacing it.
"""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.realtime.session_epoch_registry import SessionEpochRegistry


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
    # CANCELLED_RESPONSE / STALE_EVENT_SEQ / UNKNOWN_TURN: DIARY-3가 실제
    # turn·response 상태를 연결할 때 추가한다(§11-B) — 지금은 대상 밖.


class ConversationEventGuard:
    """한 Signaling 연결 동안만 유지되는 §4 폐기 규칙 검사기.

    ``conversation_id``/``realtime_session_id``는 이 연결이 인증에 성공했을 때
    확정된 값이고, 재연결마다 새 인스턴스를 만든다(영속 저장하지 않음, §11-D).
    """

    def __init__(
        self,
        conversation_id: str,
        realtime_session_id: str,
        epoch_registry: SessionEpochRegistry,
    ) -> None:
        self._conversation_id = conversation_id
        self._realtime_session_id = realtime_session_id
        self._epoch_registry = epoch_registry
        self._seen_event_ids: set[str] = set()

    def check(self, envelope: ConversationEventEnvelope) -> EventDiscardReason | None:
        if envelope.conversation_id != self._conversation_id:
            return EventDiscardReason.WRONG_CONVERSATION
        if envelope.realtime_session_id != self._realtime_session_id:
            return EventDiscardReason.WRONG_REALTIME_SESSION
        if not self._epoch_registry.is_current(envelope.conversation_id, envelope.session_epoch):
            return EventDiscardReason.STALE_SESSION_EPOCH
        if envelope.event_id in self._seen_event_ids:
            return EventDiscardReason.DUPLICATE_EVENT_ID
        self._seen_event_ids.add(envelope.event_id)
        return None
