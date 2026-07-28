"""Context Snapshot model and Redis reader tests (T11)."""

import json
import logging

import pytest

from app.dialogue.schemas import DialogueTopic
from app.realtime.context_snapshot import (
    ContextSnapshot,
    FakeContextSnapshotReader,
    RedisContextSnapshotReader,
    default_context_snapshot,
)

_LOGGER = logging.getLogger("test.realtime.context_snapshot")

# FUNCTIONAL_SPEC §8의 예시 JSON 그대로 (실명 대신 가명 — CLAUDE.md 데이터 정책)
_SNAPSHOT_JSON = {
    "contextVersion": 1,
    "conversationId": "conv_01JZ123ABC",
    "child": {"displayName": "다온"},
    "conversation": {
        "goal": "DIARY",
        "language": "ko-KR",
        "currentTopic": "GAME",
        "totalMaxQuestionCount": 12,
        "questionCount": 3,
    },
    "recentConversationSummary": "놀이터에서 놀았어 낮잠도 잤어",
    "topics": [
        {
            "type": "DAILY",
            "order": 2,
            "maxQuestionCount": 6,
            "questionCount": 0,
            "games": [],
            "initialSlots": {
                "event": None,
                "people": [],
                "place": None,
                "emotion": None,
                "emotionReason": None,
                "memorableMoment": None,
            },
        },
        {
            "type": "GAME",
            "order": 1,
            "maxQuestionCount": 6,
            "questionCount": 3,
            "games": [
                {
                    "gameSessionId": "b3b10000-0000-0000-0000-000000000001",
                    "gameId": 1,
                    "score": 820,
                    "playDurationSeconds": 180,
                    "completedAt": "2026-07-27T10:00:00",
                }
            ],
            "initialSlots": {
                "event": "과일 게임",
                "people": [],
                "place": None,
                "emotion": None,
                "emotionReason": None,
                "memorableMoment": None,
            },
        },
    ],
    "policy": {"oneQuestionAtATime": True, "avoidPressureQuestions": True},
}


class _FakeRedis:
    def __init__(self, values: dict[str, str] | None = None) -> None:
        self._values = values or {}
        self.requested: list[str] = []

    async def get(self, key: str) -> str | None:
        self.requested.append(key)
        return self._values.get(key)


class TestModel:
    def test_parses_the_contract_example(self) -> None:
        snapshot = ContextSnapshot.model_validate(_SNAPSHOT_JSON)

        assert snapshot.conversation.current_topic is DialogueTopic.GAME
        assert snapshot.conversation.total_max_question_count == 12
        assert snapshot.conversation.question_count == 3
        assert snapshot.topics[1].games[0].score == 820
        assert snapshot.topics[1].initial_slots.event == "과일 게임"
        assert snapshot.topics[1].question_count == 3

    def test_ordered_topics_sorts_by_order_not_input_position(self) -> None:
        snapshot = ContextSnapshot.model_validate(_SNAPSHOT_JSON)

        ordered = snapshot.ordered_topics()

        assert [topic.type for topic in ordered] == [DialogueTopic.GAME, DialogueTopic.DAILY]

    def test_rejects_unknown_fields(self) -> None:
        corrupted = dict(_SNAPSHOT_JSON, accessToken="never")

        with pytest.raises(Exception):
            ContextSnapshot.model_validate(corrupted)

    def test_child_speech_is_not_in_repr(self) -> None:
        """recentConversationSummary는 아이의 실제 최근 발화다(§13)."""

        snapshot = ContextSnapshot.model_validate(_SNAPSHOT_JSON)

        assert "놀이터" not in repr(snapshot)
        assert "다온" not in repr(snapshot)

    def test_default_snapshot_is_a_single_daily_topic(self) -> None:
        snapshot = default_context_snapshot("conv_x")

        assert snapshot.conversation.current_topic is DialogueTopic.DAILY
        assert len(snapshot.topics) == 1
        assert snapshot.topics[0].max_question_count == 6
        assert snapshot.topics[0].initial_slots.event is None


class TestRedisReader:
    @pytest.mark.asyncio
    async def test_reads_and_parses_the_stored_snapshot(self) -> None:
        redis = _FakeRedis({"realtime:context:conv_1": json.dumps(_SNAPSHOT_JSON)})
        reader = RedisContextSnapshotReader(redis, _LOGGER)

        snapshot = await reader.read("conv_1")

        assert snapshot is not None
        assert snapshot.conversation_id == "conv_01JZ123ABC"
        assert redis.requested == ["realtime:context:conv_1"]

    @pytest.mark.asyncio
    async def test_missing_key_returns_none(self) -> None:
        reader = RedisContextSnapshotReader(_FakeRedis(), _LOGGER)

        assert await reader.read("conv_x") is None

    @pytest.mark.asyncio
    async def test_corrupt_json_returns_none_without_logging_the_payload(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        redis = _FakeRedis({"realtime:context:conv_1": "놀이터에서 {broken"})
        reader = RedisContextSnapshotReader(redis, _LOGGER)

        with caplog.at_level(logging.WARNING, logger=_LOGGER.name):
            assert await reader.read("conv_1") is None

        assert "could not be parsed" in caplog.text
        assert "놀이터" not in caplog.text

    @pytest.mark.asyncio
    async def test_invalid_shape_returns_none(self) -> None:
        redis = _FakeRedis({"realtime:context:conv_1": json.dumps({"contextVersion": 0})})
        reader = RedisContextSnapshotReader(redis, _LOGGER)

        assert await reader.read("conv_1") is None


@pytest.mark.asyncio
async def test_fake_reader_records_reads() -> None:
    reader = FakeContextSnapshotReader(None)

    assert await reader.read("conv_9") is None
    assert reader.reads == ["conv_9"]


@pytest.mark.asyncio
async def test_redis_failure_degrades_to_none_instead_of_raising() -> None:
    """Redis 장애가 DataChannel 콜백까지 전파되면 세션 조립이 통째로 죽는다."""

    from redis.exceptions import ConnectionError as RedisConnectionError

    class _BrokenRedis:
        async def get(self, key: str) -> str:
            raise RedisConnectionError("redis is down")

    reader = RedisContextSnapshotReader(_BrokenRedis(), _LOGGER)

    assert await reader.read("conv_1") is None


def test_last_turn_seq_parses_and_defaults_to_zero() -> None:
    """§11-A: 재연결 turnSeq 연속성. 구버전 스냅숏(필드 없음)은 0으로 관용."""

    with_field = dict(_SNAPSHOT_JSON)
    with_field["conversation"] = dict(_SNAPSHOT_JSON["conversation"], lastTurnSeq=7)
    assert ContextSnapshot.model_validate(with_field).conversation.last_turn_seq == 7

    # 필드가 없는 구버전 스냅숏도 파싱되고 0이 된다(배포 순서 무관).
    assert ContextSnapshot.model_validate(_SNAPSHOT_JSON).conversation.last_turn_seq == 0
