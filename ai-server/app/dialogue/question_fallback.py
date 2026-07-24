"""Execution policy, safe outcomes, and trusted fallback questions."""

import math
from dataclasses import dataclass
from enum import StrEnum

from app.dialogue.recovery_policy import SIMPLIFIED_QUESTION_BY_TARGET
from app.dialogue.target_selector import QuestionTarget


@dataclass(frozen=True)
class QuestionGenerationExecutionPolicy:
    """Injected limits for each external operation and total attempts."""

    timeout_seconds: float
    max_attempts: int

    def __post_init__(self) -> None:
        if (
            isinstance(self.timeout_seconds, bool)
            or not isinstance(self.timeout_seconds, (int, float))
            or not math.isfinite(self.timeout_seconds)
            or self.timeout_seconds <= 0
        ):
            raise ValueError("timeout_seconds must be a positive finite number")
        if (
            isinstance(self.max_attempts, bool)
            or not isinstance(self.max_attempts, int)
            or self.max_attempts < 1
        ):
            raise ValueError("max_attempts must be a positive integer")


@dataclass(frozen=True, repr=False)
class FallbackQuestion:
    """A trusted fixed question containing no child-specific context."""

    target: QuestionTarget
    text: str

    def __post_init__(self) -> None:
        if not isinstance(self.target, QuestionTarget):
            raise ValueError("target must be a QuestionTarget")
        if self.text != SIMPLIFIED_QUESTION_BY_TARGET[self.target]:
            raise ValueError("text must match the registered fallback question")


def build_fallback_question(target: QuestionTarget) -> FallbackQuestion:
    """Build the registered T13 simplified question for one target."""

    if not isinstance(target, QuestionTarget):
        raise ValueError("target must be a QuestionTarget")
    return FallbackQuestion(
        target=target,
        text=SIMPLIFIED_QUESTION_BY_TARGET[target],
    )


class QuestionGenerationStatus(StrEnum):
    """Final state of one question-generation execution."""

    GENERATED = "generated"
    FALLBACK_USED = "fallbackUsed"
    FAILED = "failed"


class QuestionGenerationSource(StrEnum):
    """Origin of a usable question."""

    LLM = "llm"
    RULE_BASED_FALLBACK = "ruleBasedFallback"


class QuestionGenerationFailureCode(StrEnum):
    """Sanitized reason why normal generation did not complete."""

    TIMEOUT = "timeout"
    PROVIDER_TEMPORARY_FAILURE = "providerTemporaryFailure"
    PROVIDER_PERMANENT_FAILURE = "providerPermanentFailure"
    GENERATED_QUESTION_REJECTED = "generatedQuestionRejected"
    UNEXPECTED_PROVIDER_FAILURE = "unexpectedProviderFailure"
    FALLBACK_VALIDATION_FAILED = "fallbackValidationFailed"


@dataclass(frozen=True, repr=False)
class QuestionGenerationExecutionResult:
    """Safe immutable result consumed by the future T15 orchestrator."""

    status: QuestionGenerationStatus
    source: QuestionGenerationSource | None
    question: str | None
    attempts: int
    failure_code: QuestionGenerationFailureCode | None

    def __post_init__(self) -> None:
        if not isinstance(self.status, QuestionGenerationStatus):
            raise ValueError("status must be a QuestionGenerationStatus")
        if self.source is not None and not isinstance(
            self.source,
            QuestionGenerationSource,
        ):
            raise ValueError("source must be a QuestionGenerationSource")
        if (
            isinstance(self.attempts, bool)
            or not isinstance(self.attempts, int)
            or self.attempts < 1
        ):
            raise ValueError("attempts must be a positive integer")
        if self.failure_code is not None and not isinstance(
            self.failure_code,
            QuestionGenerationFailureCode,
        ):
            raise ValueError(
                "failure_code must be a QuestionGenerationFailureCode"
            )
        self._validate_state()

    def _validate_state(self) -> None:
        has_question = (
            isinstance(self.question, str) and bool(self.question.strip())
        )
        if self.status is QuestionGenerationStatus.GENERATED:
            if (
                self.source is not QuestionGenerationSource.LLM
                or not has_question
                or self.failure_code is not None
            ):
                raise ValueError("generated result fields are inconsistent")
            return
        if self.status is QuestionGenerationStatus.FALLBACK_USED:
            if (
                self.source
                is not QuestionGenerationSource.RULE_BASED_FALLBACK
                or not has_question
                or self.failure_code is None
                or self.failure_code
                is QuestionGenerationFailureCode.FALLBACK_VALIDATION_FAILED
            ):
                raise ValueError("fallback result fields are inconsistent")
            return
        if (
            self.source is not None
            or self.question is not None
            or self.failure_code
            is not QuestionGenerationFailureCode.FALLBACK_VALIDATION_FAILED
        ):
            raise ValueError("failed result fields are inconsistent")


@dataclass(frozen=True)
class QuestionGenerationLogContext:
    """Non-sensitive identifiers attached to structured generation logs."""

    conversation_id: str
    turn_id: str | None
    response_id: str | None
    provider_name: str

    def __post_init__(self) -> None:
        required = {
            "conversation_id": self.conversation_id,
            "provider_name": self.provider_name,
        }
        optional = {
            "turn_id": self.turn_id,
            "response_id": self.response_id,
        }
        if any(
            not isinstance(value, str) or not value.strip()
            for value in required.values()
        ):
            raise ValueError("required log identifiers must contain text")
        if any(
            value is not None
            and (not isinstance(value, str) or not value.strip())
            for value in optional.values()
        ):
            raise ValueError("optional log identifiers must be null or text")
