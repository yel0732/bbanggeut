"""Context Snapshot read once per realtime session (DIARY-3-T11).

Spring writes ``realtime:context:{conversationId}`` at Realtime Session
issuance (ADR-008); FastAPI reads it exactly once when the connection's
dialogue session is assembled and reuses the parsed object from memory for
every turn — never calling Spring mid-conversation.

The models mirror FUNCTIONAL_SPEC.md §8 field for field. Two of them are the
existing DIARY-1 contract models reused as-is, because the shapes already
match exactly: ``topics[].games[]`` == ``GameSessionContext`` and
``topics[].initialSlots`` == ``DiarySlots`` (aliases included). Redefining
them here would just create a second source of truth.

Question counts are persisted progress and are included so reconnecting does
not reset the policy budget. ``DialogueSessionState`` owns the mutable
in-connection copy and publishes authoritative counts with confirmed turns.
"""

from __future__ import annotations

import json
import logging

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator
from redis.exceptions import RedisError

from app.dialogue.schemas import DialogueTopic, DiarySlots, GameSessionContext

_CONTEXT_KEY_PREFIX = "realtime:context:"


class ChildContext(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True, frozen=True)

    # 이름은 표시용 최소 정보다 — 로그·예외에 노출하지 않는다(§13).
    display_name: str = Field(alias="displayName", repr=False)


class ConversationContext(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True, frozen=True)

    goal: str
    language: str
    current_topic: DialogueTopic = Field(alias="currentTopic")
    total_max_question_count: int = Field(alias="totalMaxQuestionCount", gt=0)
    question_count: int = Field(default=0, alias="questionCount", ge=0)
    # 이 대화에 저장된 마지막 turnSeq(없으면 0). 재연결 시 다음부터 발급해
    # 연결 메모리 카운터가 1로 되돌아가 충돌하는 것을 막는다(W05 §11-A).
    last_turn_seq: int = Field(default=0, alias="lastTurnSeq", ge=0)

    @model_validator(mode="after")
    def validate_question_count(self) -> "ConversationContext":
        if self.question_count > self.total_max_question_count:
            raise ValueError("questionCount cannot exceed totalMaxQuestionCount")
        return self


class TopicSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True, frozen=True)

    type: DialogueTopic
    order: int = Field(gt=0)
    max_question_count: int = Field(alias="maxQuestionCount", gt=0)
    question_count: int = Field(default=0, alias="questionCount", ge=0)
    games: tuple[GameSessionContext, ...] = ()
    initial_slots: DiarySlots = Field(alias="initialSlots", repr=False)

    @model_validator(mode="after")
    def validate_question_count(self) -> "TopicSnapshot":
        if self.question_count > self.max_question_count:
            raise ValueError("questionCount cannot exceed maxQuestionCount")
        return self


class PolicySnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True, frozen=True)

    one_question_at_a_time: bool = Field(alias="oneQuestionAtATime")
    avoid_pressure_questions: bool = Field(alias="avoidPressureQuestions")


class ContextSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True, frozen=True)

    context_version: int = Field(alias="contextVersion", ge=1)
    conversation_id: str = Field(alias="conversationId")
    child: ChildContext
    conversation: ConversationContext
    recent_conversation_summary: str | None = Field(
        default=None,
        alias="recentConversationSummary",
        repr=False,  # 아이의 실제 최근 발화다 — 로그 금지(§13)
    )
    topics: tuple[TopicSnapshot, ...] = ()
    policy: PolicySnapshot

    @model_validator(mode="after")
    def validate_question_progress(self) -> "ContextSnapshot":
        topic_total = sum(topic.question_count for topic in self.topics)
        if topic_total != self.conversation.question_count:
            raise ValueError("topic question counts must equal conversation questionCount")
        return self

    def ordered_topics(self) -> tuple[TopicSnapshot, ...]:
        return tuple(sorted(self.topics, key=lambda topic: topic.order))


def default_context_snapshot(conversation_id: str) -> ContextSnapshot:
    """Fallback when the Redis snapshot is missing or unreadable.

    A dead conversation is a worse failure for the child than a generic one,
    so the dialogue proceeds on a single DAILY topic with the contract's
    default question budget (W03 plan §7-C). The gap is logged by the caller.
    """

    return ContextSnapshot(
        contextVersion=1,
        conversationId=conversation_id,
        child=ChildContext(displayName="친구"),
        conversation=ConversationContext(
            goal="DIARY",
            language="ko-KR",
            currentTopic=DialogueTopic.DAILY,
            totalMaxQuestionCount=6,
        ),
        topics=(
            TopicSnapshot(
                type=DialogueTopic.DAILY,
                order=1,
                maxQuestionCount=6,
                initialSlots=DiarySlots(),
            ),
        ),
        policy=PolicySnapshot(oneQuestionAtATime=True, avoidPressureQuestions=True),
    )


class RedisContextSnapshotReader:
    """Reads the snapshot Spring stored at session issuance."""

    def __init__(self, redis_client, logger: logging.Logger) -> None:
        self._redis = redis_client
        self._logger = logger

    async def read(self, conversation_id: str) -> ContextSnapshot | None:
        try:
            raw = await self._redis.get(f"{_CONTEXT_KEY_PREFIX}{conversation_id}")
        except RedisError:
            # Redis 장애가 DataChannel 콜백까지 전파되면 대화 세션 조립 자체가
            # 죽는다 — 부재와 동일하게 다뤄 기본 주제 fallback으로 흘려보낸다.
            self._logger.warning(
                "context snapshot read failed",
                extra={"conversation_id": conversation_id},
            )
            return None
        if raw is None:
            return None
        try:
            return ContextSnapshot.model_validate(json.loads(raw))
        except (ValueError, ValidationError):
            # 원문(아이 최근 발화 포함 가능)은 로그에 남기지 않는다(§13).
            self._logger.warning(
                "context snapshot could not be parsed",
                extra={"conversation_id": conversation_id},
            )
            return None


class FakeContextSnapshotReader:
    """Test double returning a scripted snapshot (or None)."""

    def __init__(self, snapshot: ContextSnapshot | None = None) -> None:
        self._snapshot = snapshot
        self.reads: list[str] = []

    async def read(self, conversation_id: str) -> ContextSnapshot | None:
        self.reads.append(conversation_id)
        return self._snapshot
