"""Bounded GMS orchestration for child-facing diary lines."""

import asyncio
import json
from dataclasses import dataclass
from typing import Protocol

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator

from app.child_diary.prompts import PROMPT_VERSION, build_child_diary_messages
from app.child_diary.schemas import (
    DIARY_TITLE,
    MAX_DIARY_LINES,
    MAX_LINE_LENGTH,
    MIN_DIARY_LINES,
    ChildDiarySummaryRequest,
    ChildDiarySummaryResponse,
    ChildDiaryTopicType,
)
from app.dialogue.question.provider import QuestionProviderError
from app.providers.gms_chat import GmsChatMessage


class ChildDiaryTextProvider(Protocol):
    model: str

    async def complete(
        self,
        messages: tuple[GmsChatMessage, ...],
    ) -> str: ...


class _GeneratedDiary(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
    )

    game_lines: tuple[str, ...] = Field(
        default_factory=tuple,
        alias="gameLines",
        max_length=MAX_DIARY_LINES,
    )
    daily_lines: tuple[str, ...] = Field(
        default_factory=tuple,
        alias="dailyLines",
        max_length=MAX_DIARY_LINES,
    )

    @field_validator("game_lines", "daily_lines")
    @classmethod
    def validate_lines(cls, values: tuple[str, ...]) -> tuple[str, ...]:
        normalized = tuple(value.strip() for value in values)
        if any(not value or len(value) > MAX_LINE_LENGTH for value in normalized):
            raise ValueError("generated diary line length is invalid")
        return normalized


class ChildDiaryGenerationError(RuntimeError):
    """Sanitized provider failure exposed at the internal API boundary."""


@dataclass(frozen=True)
class ChildDiaryGenerator:
    provider: ChildDiaryTextProvider
    timeout_seconds: float
    max_attempts: int

    async def generate(
        self,
        request: ChildDiarySummaryRequest,
    ) -> ChildDiarySummaryResponse:
        messages = build_child_diary_messages(request)
        for attempt in range(self.max_attempts):
            try:
                raw = await asyncio.wait_for(
                    self.provider.complete(messages),
                    timeout=self.timeout_seconds,
                )
                generated = _parse_generated_diary(raw, request)
                lines = generated.game_lines + generated.daily_lines
                return ChildDiarySummaryResponse(
                    conversationId=request.conversation_id,
                    diaryDate=request.diary_date,
                    title=DIARY_TITLE,
                    lines=lines,
                    modelVersion=self.provider.model,
                    promptVersion=PROMPT_VERSION,
                )
            except TimeoutError:
                retryable = True
            except QuestionProviderError as error:
                retryable = error.retryable
            except (ValueError, ValidationError, json.JSONDecodeError):
                retryable = False
            if not retryable or attempt + 1 >= self.max_attempts:
                break
        raise ChildDiaryGenerationError("CHILD_DIARY_GENERATION_UNAVAILABLE")


def _parse_generated_diary(
    raw: str,
    request: ChildDiarySummaryRequest,
) -> _GeneratedDiary:
    generated = _GeneratedDiary.model_validate(json.loads(raw))
    topic_types = {topic.topic_type for topic in request.topics}
    has_game = ChildDiaryTopicType.GAME in topic_types
    has_daily = ChildDiaryTopicType.DAILY in topic_types
    if has_game and has_daily:
        if len(generated.game_lines) != 2 or len(generated.daily_lines) != 2:
            raise ValueError("combined diary requires two lines per topic")
    elif has_game:
        if not MIN_DIARY_LINES <= len(generated.game_lines) <= MAX_DIARY_LINES:
            raise ValueError("game diary requires three or four lines")
        if generated.daily_lines:
            raise ValueError("dailyLines must be empty without a DAILY topic")
    else:
        if not MIN_DIARY_LINES <= len(generated.daily_lines) <= MAX_DIARY_LINES:
            raise ValueError("daily diary requires three or four lines")
        if generated.game_lines:
            raise ValueError("gameLines must be empty without a GAME topic")
    return generated
