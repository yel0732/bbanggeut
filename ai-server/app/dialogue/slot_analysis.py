"""Timeout, retry, repair, and merge execution for T09 slot extraction."""

import asyncio
import logging
from dataclasses import dataclass
from enum import StrEnum

from app.dialogue.gms_adapters import GmsCompletionClient
from app.dialogue.parser import SlotParseError, parse_diary_slots
from app.dialogue.prompts import (
    build_slot_extraction_prompt,
    build_slot_repair_instruction,
)
from app.dialogue.question_fallback import (
    QuestionGenerationExecutionPolicy,
)
from app.dialogue.question_provider import QuestionProviderError
from app.dialogue.schemas import DiaryAnswerAnalysisRequest, DiarySlots
from app.dialogue.slot_merger import merge_diary_slots
from app.providers.gms_chat import GmsChatMessage


class SlotAnalysisFailureCode(StrEnum):
    """Safe final reasons for slot-analysis failure."""

    TIMEOUT = "timeout"
    PROVIDER_TEMPORARY_FAILURE = "providerTemporaryFailure"
    PROVIDER_PERMANENT_FAILURE = "providerPermanentFailure"
    OUTPUT_REJECTED = "outputRejected"
    UNEXPECTED_PROVIDER_FAILURE = "unexpectedProviderFailure"


@dataclass(frozen=True)
class SlotAnalysisError(Exception):
    """Sanitized failure that retains no answer or provider output."""

    code: SlotAnalysisFailureCode

    def __post_init__(self) -> None:
        Exception.__init__(self, self.code.value)

    def __str__(self) -> str:
        return self.code.value


@dataclass(frozen=True)
class SlotAnalysisLogContext:
    """Non-sensitive identifiers for structured slot-analysis logs."""

    conversation_id: str
    request_id: str
    provider_name: str = "gms"


async def analyze_answer_with_retry(
    request: DiaryAnswerAnalysisRequest,
    client: GmsCompletionClient,
    policy: QuestionGenerationExecutionPolicy,
    log_context: SlotAnalysisLogContext,
    logger: logging.Logger,
) -> DiarySlots:
    """Extract and merge one answer using bounded GMS attempts."""

    prompt = build_slot_extraction_prompt(request)
    repair_instruction: str | None = None
    failure_code = SlotAnalysisFailureCode.UNEXPECTED_PROVIDER_FAILURE

    for attempt in range(1, policy.max_attempts + 1):
        messages = [
            GmsChatMessage(role="developer", content=prompt.system),
        ]
        if repair_instruction is not None:
            messages.append(
                GmsChatMessage(
                    role="developer",
                    content=repair_instruction,
                )
            )
        messages.append(GmsChatMessage(role="user", content=prompt.user))

        try:
            async with asyncio.timeout(policy.timeout_seconds):
                raw_slots = await client.complete(tuple(messages))
        except asyncio.CancelledError:
            raise
        except TimeoutError:
            failure_code = SlotAnalysisFailureCode.TIMEOUT
            retryable = True
        except QuestionProviderError as error:
            failure_code = (
                SlotAnalysisFailureCode.PROVIDER_TEMPORARY_FAILURE
                if error.retryable
                else SlotAnalysisFailureCode.PROVIDER_PERMANENT_FAILURE
            )
            retryable = error.retryable
        except Exception:
            failure_code = (
                SlotAnalysisFailureCode.UNEXPECTED_PROVIDER_FAILURE
            )
            retryable = False
        else:
            try:
                extracted_slots = parse_diary_slots(raw_slots)
            except SlotParseError as error:
                failure_code = SlotAnalysisFailureCode.OUTPUT_REJECTED
                retryable = error.recoverable
                repair_instruction = build_slot_repair_instruction(error.code)
            else:
                return merge_diary_slots(
                    request.current_slots,
                    extracted_slots,
                )

        _log_slot_failure(
            logger,
            log_context,
            attempt,
            policy.max_attempts,
            failure_code,
        )
        if not retryable or attempt >= policy.max_attempts:
            break

    raise SlotAnalysisError(failure_code)


def _log_slot_failure(
    logger: logging.Logger,
    context: SlotAnalysisLogContext,
    attempt: int,
    max_attempts: int,
    failure_code: SlotAnalysisFailureCode,
) -> None:
    logger.warning(
        "dialogue slot analysis attempt failed",
        extra={
            "conversation_id": context.conversation_id,
            "request_id": context.request_id,
            "provider_name": context.provider_name,
            "attempt": attempt,
            "max_attempts": max_attempts,
            "failure_code": failure_code.value,
        },
    )
