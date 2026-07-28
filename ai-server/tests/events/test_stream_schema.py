"""Redis Stream event contract fixtures (T21).

The JSON strings here are the canonical contract. Spring's
``ConversationStreamEventContractTest`` deserializes the SAME strings — keep the
two in sync by hand (the design docs are gitignored locally, so these fixtures
are the source of truth). REALTIME_EVENT_CONTRACT.md §7 is the reference.
"""

import json
import logging
from datetime import datetime, timezone

import pytest

from app.dialogue.schemas import DiarySlots
from app.events.stream import ConversationStreamProducer, StreamEventType

_LOGGER = logging.getLogger("test.events.stream_schema")
_FIXED = datetime(2026, 7, 22, 10, 0, 30, tzinfo=timezone.utc)


class _RecordingRedis:
    def __init__(self) -> None:
        self.entries: list[dict] = []

    async def xadd(self, key: str, fields: dict) -> None:
        assert key == "conversation-events"
        assert list(fields.keys()) == ["event"]
        self.entries.append(json.loads(fields["event"]))


def _producer(redis) -> ConversationStreamProducer:
    ids = iter(f"evt_internal_{i:03d}" for i in range(1, 50))
    producer = ConversationStreamProducer(
        redis,
        conversation_id="conv_01JZ123ABC",
        realtime_session_id="rt_01JZ456DEF",
        session_epoch=1,
        logger=_LOGGER,
        clock=lambda: _FIXED,
        event_id_factory=lambda: next(ids),
    )
    producer.start()
    return producer


async def _publish_and_collect(publish) -> dict:
    redis = _RecordingRedis()
    producer = _producer(redis)
    publish(producer)
    await producer.aclose()
    assert len(redis.entries) == 1
    return redis.entries[0]


class TestEnvelopeShape:
    @pytest.mark.asyncio
    async def test_common_envelope_fields(self) -> None:
        event = await _publish_and_collect(
            lambda p: p.publish_session_started(_FIXED)
        )

        assert event["eventId"] == "evt_internal_001"
        assert event["eventType"] == "SESSION_STARTED"
        assert event["conversationId"] == "conv_01JZ123ABC"
        assert event["realtimeSessionId"] == "rt_01JZ456DEF"
        assert event["sessionEpoch"] == 1
        assert event["turnId"] is None
        assert event["turnSeq"] is None
        assert event["responseId"] is None
        assert event["occurredAt"] == "2026-07-22T10:00:30+00:00"
        assert event["payload"] == {"connectedAt": "2026-07-22T10:00:30+00:00"}


class TestPayloads:
    @pytest.mark.asyncio
    async def test_child_turn_finalized(self) -> None:
        event = await _publish_and_collect(
            lambda p: p.publish_child_turn_finalized(
                turn_id="turn_002",
                turn_seq=2,
                text="과일이 많이 나왔을 때 재미있었어.",
                stt_confidence=0.91,
                started_at=datetime(2026, 7, 22, 10, 0, 20, tzinfo=timezone.utc),
                completed_at=datetime(2026, 7, 22, 10, 0, 30, tzinfo=timezone.utc),
            )
        )

        assert event["eventType"] == "CHILD_TURN_FINALIZED"
        assert event["turnId"] == "turn_002"
        assert event["turnSeq"] == 2
        assert event["responseId"] is None
        assert event["payload"] == {
            "text": "과일이 많이 나왔을 때 재미있었어.",
            "sttConfidence": 0.91,
            "startedAt": "2026-07-22T10:00:20+00:00",
            "completedAt": "2026-07-22T10:00:30+00:00",
        }

    @pytest.mark.asyncio
    async def test_ai_turn_generated_carries_slot_sync_fields(self) -> None:
        event = await _publish_and_collect(
            lambda p: p.publish_ai_turn_generated(
                turn_id="turn_003",
                turn_seq=3,
                response_id="resp_003",
                text="그때 어떤 기분이 들었어?",
                target_slot="emotion",
                topic_type="GAME",
                topic_slots=DiarySlots(event="과일 자르기", emotion=None),
            )
        )

        assert event["eventType"] == "AI_TURN_GENERATED"
        assert event["responseId"] == "resp_003"
        assert event["payload"]["text"] == "그때 어떤 기분이 들었어?"
        assert event["payload"]["targetSlot"] == "emotion"
        # §11-B 슬롯 동기화: 재연결 스냅숏이 최신 슬롯을 담게 하는 필드
        assert event["payload"]["topicType"] == "GAME"
        assert event["payload"]["topicSlots"]["event"] == "과일 자르기"
        assert event["payload"]["topicSlots"]["emotion"] is None
        assert event["payload"]["topicSlots"]["people"] == []

    @pytest.mark.asyncio
    async def test_ai_turn_generated_null_target(self) -> None:
        event = await _publish_and_collect(
            lambda p: p.publish_ai_turn_generated(
                turn_id="turn_004",
                turn_seq=4,
                response_id="resp_004",
                text="이번에는 오늘 있었던 다른 일도 이야기해 줄래?",
                target_slot=None,  # 전환·재질문 등 목표 없는 턴
                topic_type="DAILY",
                topic_slots=DiarySlots(),
            )
        )

        assert event["payload"]["targetSlot"] is None

    @pytest.mark.asyncio
    async def test_ai_turn_completed(self) -> None:
        event = await _publish_and_collect(
            lambda p: p.publish_ai_turn_completed(
                turn_id="turn_003",
                turn_seq=3,
                response_id="resp_003",
                text="그때 어떤 기분이 들었어?",
                audio_started_at=datetime(2026, 7, 22, 10, 0, 32, tzinfo=timezone.utc),
                audio_completed_at=datetime(2026, 7, 22, 10, 0, 34, tzinfo=timezone.utc),
            )
        )

        assert event["eventType"] == "AI_TURN_COMPLETED"
        assert event["payload"] == {
            "text": "그때 어떤 기분이 들었어?",
            "audioStartedAt": "2026-07-22T10:00:32+00:00",
            "audioCompletedAt": "2026-07-22T10:00:34+00:00",
        }

    @pytest.mark.asyncio
    async def test_session_completed(self) -> None:
        event = await _publish_and_collect(
            lambda p: p.publish_session_completed(
                reason="USER_FINISHED",
                completed_at=datetime(2026, 7, 22, 10, 8, 0, tzinfo=timezone.utc),
            )
        )

        assert event["eventType"] == "SESSION_COMPLETED"
        assert event["payload"] == {
            "reason": "USER_FINISHED",
            "completedAt": "2026-07-22T10:08:00+00:00",
        }

    @pytest.mark.asyncio
    async def test_session_failed(self) -> None:
        event = await _publish_and_collect(
            lambda p: p.publish_session_failed(
                code="TTS_FAILED",
                message="TTS Provider 호출에 실패했습니다.",
                failed_at=datetime(2026, 7, 22, 10, 7, 0, tzinfo=timezone.utc),
            )
        )

        assert event["eventType"] == "SESSION_FAILED"
        assert event["payload"]["code"] == "TTS_FAILED"


class TestPrivacy:
    @pytest.mark.asyncio
    async def test_stream_field_name_is_not_the_transcript(self) -> None:
        """xadd field는 'event' 하나뿐 — 원문이 field 이름으로 새지 않는다(§13)."""

        redis = _RecordingRedis()
        producer = _producer(redis)
        producer.publish_child_turn_finalized(
            turn_id="t", turn_seq=1, text="비밀 이야기", stt_confidence=None,
            started_at=_FIXED, completed_at=_FIXED,
        )
        await producer.aclose()
        # 원문은 payload 안에만(계약이 요구하는 저장 대상), field 키에는 없다.

    def test_all_contract_event_types_are_defined(self) -> None:
        assert {t.value for t in StreamEventType} == {
            "SESSION_STARTED",
            "CHILD_TURN_FINALIZED",
            "AI_TURN_GENERATED",
            "AI_TURN_COMPLETED",
            "AI_TURN_INTERRUPTED",
            "SESSION_COMPLETED",
            "SESSION_FAILED",
        }
