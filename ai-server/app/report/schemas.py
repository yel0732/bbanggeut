"""Strict internal API contracts for daily report generation."""

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ReportGameInput(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    game_name: str = Field(alias="gameName", min_length=1, max_length=100)
    play_duration_seconds: int = Field(
        alias="playDurationSeconds", ge=0, le=86400
    )
    score: int = Field(ge=0)


class ReportConversationTurn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    speaker: str = Field(pattern=r"^(CHILD|AI)$")
    content: str = Field(min_length=1, max_length=500)


class ReportEmotionDetail(BaseModel):
    model_config = ConfigDict(extra="forbid")

    emotion: str = Field(min_length=1, max_length=100)
    reason: str = Field(default="", max_length=300)


class ReportConversationGuide(BaseModel):
    model_config = ConfigDict(extra="forbid")

    description: str = Field(default="", max_length=500)
    questions: tuple[str, ...] = Field(default_factory=tuple, max_length=3)


class DailyReportSummaryRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    report_date: str = Field(alias="reportDate", pattern=r"^\d{4}-\d{2}-\d{2}$")
    events: tuple[str, ...] = Field(default_factory=tuple, max_length=30)
    emotions: tuple[str, ...] = Field(default_factory=tuple, max_length=30)
    interests: tuple[str, ...] = Field(default_factory=tuple, max_length=30)
    conversations: tuple[ReportConversationTurn, ...] = Field(
        default_factory=tuple, max_length=60
    )
    games: tuple[ReportGameInput, ...] = Field(default_factory=tuple, max_length=50)

    @field_validator("events", "emotions", "interests")
    @classmethod
    def validate_texts(cls, values: tuple[str, ...]) -> tuple[str, ...]:
        cleaned = tuple(value.strip() for value in values)
        if any(not value or len(value) > 300 for value in cleaned):
            raise ValueError("report facts must contain 1 to 300 characters")
        return tuple(dict.fromkeys(cleaned))

    def has_activity(self) -> bool:
        return bool(
            self.events
            or self.emotions
            or self.interests
            or self.conversations
            or self.games
        )


class DailyReportSummaryResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    title: str = Field(min_length=1, max_length=200)
    daily_story: str = Field(alias="dailyStory", min_length=1, max_length=4000)
    emotion_details: tuple[ReportEmotionDetail, ...] = Field(
        alias="emotionDetails", default_factory=tuple, max_length=10
    )
    interests: tuple[str, ...] = Field(default_factory=tuple, max_length=10)
    notable_quotes: tuple[str, ...] = Field(
        alias="notableQuotes", default_factory=tuple, max_length=3
    )
    conversation_guide: ReportConversationGuide = Field(alias="conversationGuide")
    model_version: str = Field(alias="modelVersion", min_length=1, max_length=100)
    prompt_version: str = Field(alias="promptVersion", min_length=1, max_length=100)
