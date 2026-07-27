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


class TestDiary3DiscardRules:
    """§4의 잔여 3종 — W03(T13)에서 완성. 훅 미주입 시 검사를 건너뛴다."""

    def _guard(self, **hooks) -> ConversationEventGuard:
        registry = SessionEpochRegistry()
        registry.register("conv_1", 1)
        return ConversationEventGuard("conv_1", "rt_1", registry, **hooks)

    def test_stale_event_seq_is_rejected(self) -> None:
        guard = self._guard()
        assert guard.check(
            ConversationEventEnvelope.model_validate(_raw_envelope(eventSeq=5, eventId="evt_a"))
        ) is None

        # 같은 seq 재수신도, 더 낮은 seq도 폐기된다
        assert guard.check(
            ConversationEventEnvelope.model_validate(_raw_envelope(eventSeq=5, eventId="evt_b"))
        ) is EventDiscardReason.STALE_EVENT_SEQ
        assert guard.check(
            ConversationEventEnvelope.model_validate(_raw_envelope(eventSeq=3, eventId="evt_c"))
        ) is EventDiscardReason.STALE_EVENT_SEQ

    def test_event_seq_advances_only_on_accepted_events(self) -> None:
        """폐기된 이벤트는 '이미 처리한' 것이 아니므로 수열을 전진시키지 않는다."""

        guard = self._guard(is_known_turn=lambda turn_id: False)
        rejected = ConversationEventEnvelope.model_validate(
            _raw_envelope(eventSeq=7, eventId="evt_a")
        )
        assert guard.check(rejected) is EventDiscardReason.UNKNOWN_TURN

        accepted = ConversationEventEnvelope.model_validate(
            _raw_envelope(eventSeq=7, eventId="evt_b", turnId=None, turnSeq=None)
        )
        assert guard.check(accepted) is None

    def test_cancelled_response_is_rejected(self) -> None:
        guard = self._guard(is_response_cancelled=lambda response_id: response_id == "resp_9")

        rejected = ConversationEventEnvelope.model_validate(
            _raw_envelope(responseId="resp_9", eventId="evt_a")
        )
        accepted = ConversationEventEnvelope.model_validate(
            _raw_envelope(responseId="resp_1", eventId="evt_b", eventSeq=19)
        )

        assert guard.check(rejected) is EventDiscardReason.CANCELLED_RESPONSE
        assert guard.check(accepted) is None

    def test_unknown_turn_is_rejected(self) -> None:
        guard = self._guard(is_known_turn=lambda turn_id: turn_id == "turn_002")

        assert guard.check(
            ConversationEventEnvelope.model_validate(_raw_envelope(eventId="evt_a"))
        ) is None
        assert guard.check(
            ConversationEventEnvelope.model_validate(
                _raw_envelope(turnId="turn_ghost", eventId="evt_b", eventSeq=19)
            )
        ) is EventDiscardReason.UNKNOWN_TURN

    def test_none_identifiers_skip_the_hook_checks(self) -> None:
        """client.ready처럼 turnId/responseId가 없는 이벤트는 검사 대상이 아니다."""

        guard = self._guard(
            is_response_cancelled=lambda _: True,
            is_known_turn=lambda _: False,
        )
        envelope = ConversationEventEnvelope.model_validate(
            _raw_envelope(turnId=None, turnSeq=None, responseId=None, eventId="evt_a")
        )

        assert guard.check(envelope) is None

    def test_without_hooks_behaviour_matches_w04(self) -> None:
        guard = self._guard()
        envelope = ConversationEventEnvelope.model_validate(
            _raw_envelope(responseId="resp_9", turnId="turn_ghost", eventId="evt_a")
        )

        assert guard.check(envelope) is None
