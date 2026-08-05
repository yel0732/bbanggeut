"""Bounded provider orchestration and strict report response parsing."""

import asyncio
import json
from dataclasses import dataclass
from typing import Protocol

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    ValidationError,
    field_validator,
)

from app.dialogue.question.provider import QuestionProviderError
from app.report.prompts import PROMPT_VERSION, build_report_messages
from app.report.schemas import (
    MAX_NOTABLE_QUOTES,
    DailyReportSummaryRequest,
    DailyReportSummaryResponse,
    ReportConversationGuide,
    ReportEmotionDetail,
)


class ReportTextProvider(Protocol):
    model: str

    async def complete(self, messages: tuple) -> str: ...


class _GeneratedNarrative(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)

    title: str = Field(min_length=1, max_length=200)
    daily_story: str = Field(alias="dailyStory", min_length=1, max_length=4000)
    emotion_details: tuple[ReportEmotionDetail, ...] = Field(
        alias="emotionDetails", default_factory=tuple, max_length=10
    )
    interests: tuple[str, ...] = Field(default_factory=tuple, max_length=10)
    # 상한을 넘겼다고 리포트 전체를 버리지 않는다. 백엔드도 verifiedQuotes에서 세 개로
    # 자르므로, 여기서 거절하면 같은 결과를 두고 리포트만 잃는다.
    notable_quotes: tuple[str, ...] = Field(
        alias="notableQuotes", default_factory=tuple, max_length=30
    )
    conversation_guide: ReportConversationGuide = Field(alias="conversationGuide")

    @field_validator("notable_quotes")
    @classmethod
    def keep_first_quotes(cls, values: tuple[str, ...]) -> tuple[str, ...]:
        return values[:MAX_NOTABLE_QUOTES]


class ReportGenerationError(RuntimeError):
    """Sanitized error crossing the internal HTTP boundary."""


class _NarrativeVoiceError(ValueError):
    """Generated story broke a voice or sourcing rule.

    스키마 오류와 달리 같은 입력으로 다시 시키면 고쳐지는 종류라 재시도 대상이다.
    여기서 폴백으로 넘기면 결정론적 템플릿(`-다`체)이 나가 문체가 더 어긋난다.
    """


# 아이 답변을 다시 서술하지 않고 AI 발화를 그대로 옮겼는지 볼 때 쓰는 하한.
# 짧은 조각은 우연히 겹칠 수 있어 문장 수준 길이부터 본다.
_MIN_COPIED_TURN_LENGTH = 8


def _assert_report_voice(
    narrative: _GeneratedNarrative,
    request: DailyReportSummaryRequest,
) -> None:
    """Reject a story that changed voice or leaned on the assistant's words."""

    for sentence in _split_sentences(narrative.daily_story):
        if not sentence.endswith("요"):
            raise _NarrativeVoiceError("dailyStory must stay in the -요 voice")

    story = narrative.daily_story
    for turn in request.conversations:
        if turn.speaker != "AI":
            continue
        spoken = turn.content.strip()
        if len(spoken) >= _MIN_COPIED_TURN_LENGTH and spoken in story:
            raise _NarrativeVoiceError("dailyStory must not copy an AI turn")


def _split_sentences(story: str) -> tuple[str, ...]:
    sentences: list[str] = []
    current = story
    for mark in (".", "!", "?", "。"):
        current = current.replace(mark, "\n")
    for chunk in current.split("\n"):
        trimmed = chunk.strip().strip("\"'“”‘’)]")
        if trimmed:
            sentences.append(trimmed)
    return tuple(sentences)


@dataclass(frozen=True)
class DailyReportGenerator:
    provider: ReportTextProvider
    timeout_seconds: float
    max_attempts: int

    async def generate(
        self, request: DailyReportSummaryRequest
    ) -> DailyReportSummaryResponse:
        messages = build_report_messages(request)
        for attempt in range(self.max_attempts):
            try:
                raw = await asyncio.wait_for(
                    self.provider.complete(messages),
                    timeout=self.timeout_seconds,
                )
                narrative = _parse_narrative(raw)
                _assert_report_voice(narrative, request)
                return DailyReportSummaryResponse(
                    title=narrative.title.strip(),
                    dailyStory=narrative.daily_story.strip(),
                    emotionDetails=narrative.emotion_details,
                    interests=narrative.interests,
                    notableQuotes=narrative.notable_quotes,
                    conversationGuide=narrative.conversation_guide,
                    modelVersion=self.provider.model,
                    promptVersion=PROMPT_VERSION,
                )
            except TimeoutError:
                retryable = True
            except QuestionProviderError as error:
                retryable = error.retryable
            # _NarrativeVoiceError는 ValueError 하위라 아래 분기보다 먼저 잡아야 한다.
            except _NarrativeVoiceError:
                retryable = True
            except (ValueError, ValidationError, json.JSONDecodeError):
                retryable = False
            if not retryable or attempt + 1 >= self.max_attempts:
                break
        raise ReportGenerationError("REPORT_GENERATION_UNAVAILABLE")


def _parse_narrative(raw: str) -> _GeneratedNarrative:
    payload = json.loads(raw)
    narrative = _GeneratedNarrative.model_validate(payload)
    if not narrative.title.strip() or not narrative.daily_story.strip():
        raise ValueError("generated report must contain text")
    return narrative
