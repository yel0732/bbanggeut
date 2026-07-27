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

What the snapshot deliberately does NOT carry (§8): per-topic question counts
— "질문 수는 스냅숏에 포함하지 않고 FastAPI가 매 턴 이후 자체 메모리로
관리한다". That memory is ``DialogueSessionState`` (T12).
"""

from __future__ import annotations

import json
import logging

from pydantic import BaseModel, ConfigDict, Field, ValidationError
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


class TopicSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True, frozen=True)

    type: DialogueTopic
    order: int = Field(gt=0)
    max_question_count: int = Field(alias="maxQuestionCount", gt=0)
    games: tuple[GameSessionContext, ...] = ()
    initial_slots: DiarySlots = Field(alias="initialSlots", repr=False)


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
