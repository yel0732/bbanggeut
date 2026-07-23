"""Data models shared by dialogue manager components."""

from pydantic import BaseModel, ConfigDict, Field


class DiarySlots(BaseModel):
    """Diary details explicitly confirmed from a child's utterances."""

    model_config = ConfigDict(
        extra="forbid",
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
    result: str | None = Field(
        default=None,
        description="Result of the event explicitly mentioned by the child.",
    )
