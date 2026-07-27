"""Tests for the common DataChannel Envelope model and discard-rule guard (T14-a)."""

import pytest
from pydantic import ValidationError

from app.events.envelope import (
    ConversationEventEnvelope,
    ConversationEventGuard,
    EventDiscardReason,
)
from app.realtime.session_epoch_registry import SessionEpochRegistry


def _raw_envelope(**overrides) -> dict:
    base = {
        "type": "stt.final",
        "eventId": "evt_105",
        "conversationId": "conv_1",
        "realtimeSessionId": "rt_1",
        "sessionEpoch": 1,
        "turnId": "turn_002",
        "turnSeq": 2,
        "responseId": None,
        "eventSeq": 18,
        "occurredAt": "2026-07-22T10:01:00.215+09:00",
        "payload": {"text": "hi"},
    }
    base.update(overrides)
    return base


def test_parses_full_envelope() -> None:
    envelope = ConversationEventEnvelope.model_validate(_raw_envelope())

    assert envelope.event_id == "evt_105"
    assert envelope.conversation_id == "conv_1"
    assert envelope.realtime_session_id == "rt_1"
    assert envelope.session_epoch == 1
    assert envelope.turn_id == "turn_002"
    assert envelope.turn_seq == 2
    assert envelope.response_id is None
    assert envelope.event_seq == 18
    assert envelope.payload == {"text": "hi"}


def test_parses_envelope_without_turn_fields() -> None:
    raw = {
        "type": "client.ready",
        "eventId": "evt_client_001",
        "conversationId": "conv_1",
        "realtimeSessionId": "rt_1",
        "sessionEpoch": 1,
        "eventSeq": 1,
        "occurredAt": "2026-07-22T10:00:10+09:00",
        "payload": {"microphoneReady": True},
    }

    envelope = ConversationEventEnvelope.model_validate(raw)

    assert envelope.turn_id is None
    assert envelope.turn_seq is None
    assert envelope.response_id is None


def test_rejects_naive_occurred_at() -> None:
    with pytest.raises(ValidationError):
        ConversationEventEnvelope.model_validate(
            _raw_envelope(occurredAt="2026-07-22T10:01:00.215")
        )


def test_rejects_extra_field() -> None:
    with pytest.raises(ValidationError):
        ConversationEventEnvelope.model_validate(_raw_envelope(unexpected="field"))


def test_guard_accepts_matching_envelope() -> None:
    guard = ConversationEventGuard("conv_1", "rt_1", SessionEpochRegistry())

    reason = guard.check(ConversationEventEnvelope.model_validate(_raw_envelope()))

    assert reason is None


def test_guard_rejects_wrong_conversation_id() -> None:
    guard = ConversationEventGuard("conv_1", "rt_1", SessionEpochRegistry())

    reason = guard.check(
        ConversationEventEnvelope.model_validate(_raw_envelope(conversationId="conv_other"))
    )

    assert reason is EventDiscardReason.WRONG_CONVERSATION


def test_guard_rejects_wrong_realtime_session_id() -> None:
    guard = ConversationEventGuard("conv_1", "rt_1", SessionEpochRegistry())

    reason = guard.check(
        ConversationEventEnvelope.model_validate(_raw_envelope(realtimeSessionId="rt_other"))
    )

    assert reason is EventDiscardReason.WRONG_REALTIME_SESSION


def test_guard_rejects_stale_session_epoch() -> None:
    epoch_registry = SessionEpochRegistry()
    epoch_registry.register("conv_1", 2)
    guard = ConversationEventGuard("conv_1", "rt_1", epoch_registry)

    reason = guard.check(
        ConversationEventEnvelope.model_validate(_raw_envelope(sessionEpoch=1))
    )

    assert reason is EventDiscardReason.STALE_SESSION_EPOCH


def test_guard_accepts_when_conversation_epoch_unregistered() -> None:
    # SessionEpochRegistry.is_current() 규약: 등록되지 않은 conversationId는 최신으로 간주한다.
    guard = ConversationEventGuard("conv_1", "rt_1", SessionEpochRegistry())

    reason = guard.check(ConversationEventEnvelope.model_validate(_raw_envelope(sessionEpoch=1)))

    assert reason is None


def test_guard_rejects_duplicate_event_id() -> None:
    guard = ConversationEventGuard("conv_1", "rt_1", SessionEpochRegistry())
    envelope = ConversationEventEnvelope.model_validate(_raw_envelope())

    first = guard.check(envelope)
    second = guard.check(envelope)

    assert first is None
    assert second is EventDiscardReason.DUPLICATE_EVENT_ID


def test_guard_does_not_record_event_id_when_rejected_for_other_reasons() -> None:
    guard = ConversationEventGuard("conv_1", "rt_1", SessionEpochRegistry())
    wrong_conversation = ConversationEventEnvelope.model_validate(
        _raw_envelope(conversationId="conv_other")
    )
    same_event_id_correct_conversation = ConversationEventEnvelope.model_validate(_raw_envelope())

    guard.check(wrong_conversation)
    reason = guard.check(same_event_id_correct_conversation)

    assert reason is None  # 앞선 거부는 eventId를 "본 것"으로 기록하지 않았다
