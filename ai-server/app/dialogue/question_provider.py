"""Provider-neutral contracts for asynchronous question generation."""

from dataclasses import dataclass
from enum import StrEnum
from typing import Protocol

from app.dialogue.question_prompts import (
    DiaryQuestionGenerationRequest,
    QuestionGenerationPrompt,
)
from app.dialogue.question_validator import QuestionValidationResult


class QuestionGenerationProvider(Protocol):
    """Generate one question candidate without exposing an SDK type."""

    async def generate_question(
        self,
        prompt: QuestionGenerationPrompt,
    ) -> str:
        """Return only the generated question candidate."""


class GeneratedQuestionValidator(Protocol):
    """Validate one generated candidate, including semantic checks."""

    async def validate_question(
        self,
        request: DiaryQuestionGenerationRequest,
        question: str,
    ) -> QuestionValidationResult:
        """Return the shared T12 validation result."""


class QuestionProviderErrorCode(StrEnum):
    """Provider-independent error categories safe to expose in logs."""

    TEMPORARY_UNAVAILABLE = "temporaryUnavailable"
    RATE_LIMITED = "rateLimited"
    INVALID_REQUEST = "invalidRequest"
    AUTHENTICATION_FAILED = "authenticationFailed"
    UNKNOWN_PROVIDER_ERROR = "unknownProviderError"


@dataclass(frozen=True)
class QuestionProviderError(Exception):
    """Sanitized provider failure without an SDK message or response."""

    code: QuestionProviderErrorCode
    retryable: bool

    def __post_init__(self) -> None:
        if not isinstance(self.code, QuestionProviderErrorCode):
            raise ValueError("code must be a QuestionProviderErrorCode")
        if not isinstance(self.retryable, bool):
            raise ValueError("retryable must be a boolean")
        Exception.__init__(self, self.code.value)

    def __str__(self) -> str:
        return self.code.value
