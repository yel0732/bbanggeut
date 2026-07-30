"""Data models shared by dialogue manager components."""

from datetime import datetime
from enum import StrEnum
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class DiarySlots(BaseModel):
    """Diary details explicitly confirmed from a child's utterances."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        validate_assignment=True,
    )

    event: str | None = Field(
        default=None,
        description="Event explicitly mentioned by the child.",
    )
    people: list[str] = Field(
        default_factory=list,
        description="People explicitly mentioned by the child.",
    )
    place: str | None = Field(
        default=None,
        description="Place explicitly mentioned by the child.",
    )
    emotion: str | None = Field(
        default=None,
        description="Emotion explicitly expressed by the child.",
    )
    emotion_reason: str | None = Field(
        default=None,
        alias="emotionReason",
        description="Reason for the emotion explicitly mentioned by the child.",
    )
    memorable_moment: str | None = Field(
        default=None,
        alias="memorableMoment",
        description="Memorable moment explicitly mentioned by the child.",
    )
class GameContext(BaseModel):
    """Minimal game summary that may help interpret a child's answer."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        strict=True,
        validate_assignment=True,
    )

    game_type: str | None = Field(default=None, alias="gameType")
    score: int | None = None
    accuracy: float | None = None
    notable_events: list[str] = Field(default_factory=list, alias="notableEvents")


class DialogueTopic(StrEnum):
    """A server-owned major topic in one diary conversation."""

    GAME = "GAME"
    DAILY = "DAILY"


class GameSessionContext(BaseModel):
    """Immutable summary of one completed game included in a conversation."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        frozen=True,
    )

    game_session_id: UUID = Field(alias="gameSessionId")
    game_id: int = Field(alias="gameId")
    # 카탈로그에 없는 게임이면 Spring이 null을 보낸다 — 이름 없이도 대화는 진행된다.
    game_name: str | None = Field(default=None, alias="gameName")
    score: int
    play_duration_seconds: int = Field(alias="playDurationSeconds", ge=0)
    completed_at: datetime = Field(alias="completedAt")


class DialogueTopicContext(BaseModel):
    """Snapshot supplied by Spring for the active dialogue topic."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        frozen=True,
    )

    topic: DialogueTopic
    games: tuple[GameSessionContext, ...] = ()


class DialogueTopicHistoryItem(BaseModel):
    """One completed question-and-answer pair from the active topic."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        frozen=True,
    )

    assistant_message: str = Field(alias="assistantMessage", repr=False)
    assistant_question: str = Field(alias="assistantQuestion", repr=False)
    child_answer: str = Field(alias="childAnswer", repr=False)

    @field_validator(
        "assistant_message",
        "assistant_question",
        "child_answer",
    )
    @classmethod
    def normalize_history_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("topic history text must contain text")
        return normalized


class DiaryAnswerAnalysisRequest(BaseModel):
    """Minimal context required to extract diary slots from one final answer."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        strict=True,
        validate_assignment=True,
    )

    current_question: str | None = Field(
        default=None,
        alias="currentQuestion",
        repr=False,
    )
    child_answer: str = Field(alias="childAnswer", repr=False)
    # childAnswer가 STT 결과일 때의 신뢰도(0~1). None이면 타이핑 등 정확한 텍스트
    # 입력이다 — 분석 프롬프트가 오인식 해석을 시도할지 판단하는 근거(§22).
    stt_confidence: float | None = Field(
        default=None,
        alias="sttConfidence",
        ge=0.0,
        le=1.0,
    )
    current_slots: DiarySlots = Field(alias="currentSlots", repr=False)
    game_context: GameContext | DialogueTopicContext | None = Field(
        default=None,
        alias="gameContext",
        repr=False,
    )

    @field_validator("current_question")
    @classmethod
    def strip_optional_current_question(
        cls,
        value: str | None,
    ) -> str | None:
        """Normalize a delivered question while allowing the first free turn."""

        if value is None:
            return None
        stripped = value.strip()
        if not stripped:
            raise ValueError("must contain non-whitespace text")
        return stripped

    @field_validator("child_answer")
    @classmethod
    def strip_and_reject_blank_text(cls, value: str) -> str:
        """Normalize outer whitespace and reject text without content."""

        stripped = value.strip()
        if not stripped:
            raise ValueError("must contain non-whitespace text")
        return stripped
