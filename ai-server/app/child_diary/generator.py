"""Bounded GMS orchestration for child-facing diary lines."""

import asyncio
import json
import logging
from dataclasses import dataclass, field
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
    logger: logging.Logger = field(
        default_factory=lambda: logging.getLogger("app.child_diary.generator")
    )

    async def generate(
        self,
        request: ChildDiarySummaryRequest,
    ) -> ChildDiarySummaryResponse:
        messages = build_child_diary_messages(request)
        # 실패 원인 카테고리만 추적한다. 원문·슬롯·LLM 응답은 절대 담지 않는다.
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
            reason = "timeout"
        except QuestionProviderError as error:
            reason = f"provider:{error.code.value}"
        # 아래 세 분기는 상속 관계상 하위 타입을 먼저 잡아야 한다
        # (json.JSONDecodeError·pydantic.ValidationError 모두 ValueError 하위).
        except json.JSONDecodeError:
            reason = "invalidOutput:notJson"
        except ValidationError as error:
            # 필드 경로·제약 코드만. 입력 값은 hide_input_in_errors로 숨겨진다.
            codes = sorted(
                {
                    str(item["type"])
                    for item in error.errors(
                        include_url=False,
                        include_input=False,
                    )
                }
            )
            reason = "invalidOutput:schema:" + ",".join(codes)
        except ValueError as error:
            # _parse_generated_diary의 정적 규칙 메시지(내용 없음)만 담긴다.
            reason = f"invalidOutput:rule:{error}"
        # 왜 503으로 폴백되는지 CLI에서 보이도록 남긴다(안전한 카테고리 + 식별자만).
        self.logger.warning(
            "child diary generation unavailable: reason=%s conversationId=%s attempts=1",
            reason,
            request.conversation_id,
        )
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
        total = len(generated.game_lines) + len(generated.daily_lines)
        if (
            len(generated.game_lines) < 1
            or len(generated.daily_lines) < 1
            or not MIN_DIARY_LINES <= total <= MAX_DIARY_LINES
        ):
            raise ValueError(
                "combined diary requires three to five lines "
                "with at least one line per topic"
            )
    elif has_game:
        if not MIN_DIARY_LINES <= len(generated.game_lines) <= MAX_DIARY_LINES:
            raise ValueError("game diary requires three to five lines")
        if generated.daily_lines:
            raise ValueError("dailyLines must be empty without a DAILY topic")
    else:
        if not MIN_DIARY_LINES <= len(generated.daily_lines) <= MAX_DIARY_LINES:
            raise ValueError("daily diary requires three to five lines")
        if generated.game_lines:
            raise ValueError("gameLines must be empty without a GAME topic")
    return generated
