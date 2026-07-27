"""Bounded provider orchestration and strict report response parsing."""

import asyncio
import json
from dataclasses import dataclass
from typing import Protocol

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from app.dialogue.question.provider import QuestionProviderError
from app.report.prompts import PROMPT_VERSION, build_report_messages
from app.report.schemas import DailyReportSummaryRequest, DailyReportSummaryResponse


class ReportTextProvider(Protocol):
    model: str

    async def complete(self, messages: tuple) -> str: ...


class _GeneratedNarrative(BaseModel):
    model_config = ConfigDict(extra="forbid", hide_input_in_errors=True)

    title: str = Field(min_length=1, max_length=200)
    body: str = Field(min_length=1, max_length=2000)


class ReportGenerationError(RuntimeError):
    """Sanitized error crossing the internal HTTP boundary."""


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
                return DailyReportSummaryResponse(
                    title=narrative.title.strip(),
                    body=narrative.body.strip(),
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
        raise ReportGenerationError("REPORT_GENERATION_UNAVAILABLE")


def _parse_narrative(raw: str) -> _GeneratedNarrative:
    payload = json.loads(raw)
    narrative = _GeneratedNarrative.model_validate(payload)
    if not narrative.title.strip() or not narrative.body.strip():
        raise ValueError("generated report must contain text")
    return narrative
