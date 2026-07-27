"""Strict contracts for one conversation-scoped child diary."""

from datetime import date
from enum import StrEnum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)


DIARY_TITLE = "오늘의 일기"
MIN_DIARY_LINES = 3
MAX_DIARY_LINES = 4
MAX_LINE_LENGTH = 40


class ChildDiarySlots(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    event: str | None = Field(default=None, max_length=300)
    people: tuple[str, ...] = Field(default_factory=tuple, max_length=20)
    place: str | None = Field(default=None, max_length=100)
    emotion: str | None = Field(default=None, max_length=100)
    emotion_reason: str | None = Field(
        default=None,
        alias="emotionReason",
        max_length=300,
    )
    memorable_moment: str | None = Field(
        default=None,
        alias="memorableMoment",
        max_length=300,
    )

    @field_validator(
        "event",
        "place",
        "emotion",
        "emotion_reason",
        "memorable_moment",
    )
    @classmethod
    def normalize_optional_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None

    @field_validator("people")
    @classmethod
    def normalize_people(cls, values: tuple[str, ...]) -> tuple[str, ...]:
        normalized = tuple(value.strip() for value in values)
        if any(not value or len(value) > 50 for value in normalized):
            raise ValueError("people must contain 1 to 50 characters")
        return tuple(dict.fromkeys(normalized))

    def has_content(self) -> bool:
        return bool(
            self.event
            or self.people
            or self.place
            or self.emotion
            or self.emotion_reason
            or self.memorable_moment
        )


class ChildDiaryTopicType(StrEnum):
    GAME = "GAME"
    DAILY = "DAILY"


class ChildDiaryTopic(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    topic_type: ChildDiaryTopicType = Field(alias="topicType")
    slots: ChildDiarySlots

    @model_validator(mode="after")
    def require_topic_content(self) -> "ChildDiaryTopic":
        if not self.slots.has_content():
            raise ValueError("each topic requires validated conversation facts")
        return self


class ChildDiarySummaryRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    conversation_id: str = Field(
        alias="conversationId", min_length=1, max_length=100
    )
    diary_date: date = Field(alias="diaryDate")
    topics: tuple[ChildDiaryTopic, ...] = Field(min_length=1, max_length=2)

    @field_validator("conversation_id")
    @classmethod
    def normalize_conversation_id(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("conversationId must contain text")
        return normalized

    @model_validator(mode="after")
    def require_unique_topics(self) -> "ChildDiarySummaryRequest":
        topic_types = [topic.topic_type for topic in self.topics]
        if len(topic_types) != len(set(topic_types)):
            raise ValueError("topicType must be unique within one conversation")
        return self


class ChildDiarySummaryResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    conversation_id: str = Field(
        alias="conversationId", min_length=1, max_length=100
    )
    diary_date: date = Field(alias="diaryDate")
    title: str = Field(pattern=f"^{DIARY_TITLE}$")
    lines: tuple[str, ...] = Field(
        min_length=MIN_DIARY_LINES,
        max_length=MAX_DIARY_LINES,
    )
    model_version: str = Field(alias="modelVersion", min_length=1, max_length=100)
    prompt_version: str = Field(alias="promptVersion", min_length=1, max_length=100)

    @field_validator("lines")
    @classmethod
    def validate_lines(cls, values: tuple[str, ...]) -> tuple[str, ...]:
        normalized = tuple(value.strip() for value in values)
        if any(not value or len(value) > MAX_LINE_LENGTH for value in normalized):
            raise ValueError("each diary line must contain 1 to 40 characters")
        return normalized
