"""Data models shared by dialogue manager components."""

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
    result: str | None = Field(
        default=None,
        description="Result of the event explicitly mentioned by the child.",
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


class DiaryAnswerAnalysisRequest(BaseModel):
    """Minimal context required to extract diary slots from one final answer."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        strict=True,
        validate_assignment=True,
    )

    current_question: str = Field(alias="currentQuestion", repr=False)
    child_answer: str = Field(alias="childAnswer", repr=False)
    current_slots: DiarySlots = Field(alias="currentSlots", repr=False)
    game_context: GameContext | None = Field(
        default=None,
        alias="gameContext",
        repr=False,
    )

    @field_validator("current_question", "child_answer")
    @classmethod
    def strip_and_reject_blank_text(cls, value: str) -> str:
        """Normalize outer whitespace and reject text without content."""

        stripped = value.strip()
        if not stripped:
            raise ValueError("must contain non-whitespace text")
        return stripped
