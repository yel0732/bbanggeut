"""Async tests for timeout, retry, validation, fallback, and cancellation."""

import asyncio
import logging
from collections import deque
from collections.abc import Sequence

import pytest

from app.dialogue import (
    DiaryQuestionGenerationRequest,
    DiarySlots,
    QuestionGenerationExecutionPolicy,
    QuestionGenerationFailureCode,
    QuestionGenerationLogContext,
    QuestionGenerationPrompt,
    QuestionGenerationStatus,
    QuestionProviderError,
    QuestionProviderErrorCode,
    QuestionTarget,
    QuestionValidationCode,
    QuestionValidationIssue,
    QuestionValidationResult,
    generate_question_with_fallback,
)


class FakeProvider:
    def __init__(self, outcomes: Sequence[str | BaseException]) -> None:
        self.outcomes = deque(outcomes)
        self.prompts: list[QuestionGenerationPrompt] = []

    async def generate_question(
        self,
        prompt: QuestionGenerationPrompt,
    ) -> str:
        self.prompts.append(prompt)
        outcome = self.outcomes.popleft()
        if isinstance(outcome, BaseException):
            raise outcome
        return outcome


class FakeValidator:
    def __init__(
        self,
        outcomes: Sequence[QuestionValidationResult | BaseException],
    ) -> None:
        self.outcomes = deque(outcomes)
        self.questions: list[str] = []

    async def validate_question(
        self,
        request: DiaryQuestionGenerationRequest,
        question: str,
    ) -> QuestionValidationResult:
        self.questions.append(question)
        outcome = self.outcomes.popleft()
        if isinstance(outcome, BaseException):
            raise outcome
        return outcome


class BlockingProvider:
    def __init__(self) -> None:
        self.started = asyncio.Event()
        self.cleaned_up = asyncio.Event()

    async def generate_question(
        self,
        prompt: QuestionGenerationPrompt,
    ) -> str:
        self.started.set()
        try:
            await asyncio.Event().wait()
        finally:
            self.cleaned_up.set()
        return "도달할 수 없는 질문?"


class BlockingValidator:
    def __init__(self) -> None:
        self.started = asyncio.Event()
        self.cleaned_up = asyncio.Event()

    async def validate_question(
        self,
        request: DiaryQuestionGenerationRequest,
        question: str,
    ) -> QuestionValidationResult:
        self.started.set()
        try:
            await asyncio.Event().wait()
        finally:
            self.cleaned_up.set()
        return _valid("도달할 수 없는 질문?")


def _request(
    *,
    recent_questions: tuple[str, ...] = (),
) -> DiaryQuestionGenerationRequest:
    return DiaryQuestionGenerationRequest(
        target=QuestionTarget.EMOTION,
        current_slots=DiarySlots(event="비공개 사건"),
        recent_questions=recent_questions,
    )


def _valid(question: str) -> QuestionValidationResult:
    return QuestionValidationResult(
        normalized_question=question,
        issues=(),
    )


def _invalid(question: str) -> QuestionValidationResult:
    return QuestionValidationResult(
        normalized_question=question,
        issues=(
            QuestionValidationIssue(
                QuestionValidationCode.TARGET_MISMATCH,
            ),
        ),
    )


def _policy(*, attempts: int = 2) -> QuestionGenerationExecutionPolicy:
    return QuestionGenerationExecutionPolicy(
        timeout_seconds=0.02,
        max_attempts=attempts,
    )


def _log_context() -> QuestionGenerationLogContext:
    return QuestionGenerationLogContext(
        conversation_id="conversation-1",
        turn_id="turn-1",
        response_id=None,
        provider_name="fake",
    )


@pytest.mark.asyncio
async def test_returns_first_valid_generated_question() -> None:
    provider = FakeProvider(["  그때 어떤 기분이었어?  "])
    validator = FakeValidator([_valid("그때 어떤 기분이었어?")])

    result = await generate_question_with_fallback(
        _request(),
        provider,
        validator,
        _policy(),
        _log_context(),
        logging.getLogger("test.success"),
    )

    assert result.status is QuestionGenerationStatus.GENERATED
    assert result.question == "그때 어떤 기분이었어?"
    assert result.attempts == 1
    assert len(provider.prompts) == 1


@pytest.mark.asyncio
async def test_retries_timeout_then_returns_generated_question() -> None:
    provider = FakeProvider(
        [asyncio.TimeoutError(), "그때 마음이 어땠어?"]
    )
    validator = FakeValidator([_valid("그때 마음이 어땠어?")])

    result = await generate_question_with_fallback(
        _request(),
        provider,
        validator,
        _policy(),
        _log_context(),
        logging.getLogger("test.timeout.retry"),
    )

    assert result.status is QuestionGenerationStatus.GENERATED
    assert result.attempts == 2
    assert provider.prompts[0] == provider.prompts[1]


@pytest.mark.asyncio
async def test_retries_rejected_question_with_unchanged_prompt() -> None:
    provider = FakeProvider(["유도된 질문?", "어떤 마음이 들었어?"])
    validator = FakeValidator(
        [_invalid("유도된 질문?"), _valid("어떤 마음이 들었어?")]
    )

    result = await generate_question_with_fallback(
        _request(),
        provider,
        validator,
        _policy(),
        _log_context(),
        logging.getLogger("test.rejected.retry"),
    )

    assert result.status is QuestionGenerationStatus.GENERATED
    assert result.question == "어떤 마음이 들었어?"
    assert provider.prompts[0] is provider.prompts[1]


@pytest.mark.asyncio
async def test_retries_validator_timeout() -> None:
    provider = FakeProvider(["첫 질문?", "두 번째 질문?"])
    validator = FakeValidator(
        [asyncio.TimeoutError(), _valid("두 번째 질문?")]
    )

    result = await generate_question_with_fallback(
        _request(),
        provider,
        validator,
        _policy(),
        _log_context(),
        logging.getLogger("test.validator.timeout"),
    )

    assert result.status is QuestionGenerationStatus.GENERATED
    assert result.attempts == 2


@pytest.mark.asyncio
async def test_exhausted_retryable_error_uses_registered_fallback() -> None:
    error = QuestionProviderError(
        QuestionProviderErrorCode.RATE_LIMITED,
        retryable=True,
    )
    provider = FakeProvider([error, error])

    result = await generate_question_with_fallback(
        _request(),
        provider,
        FakeValidator([]),
        _policy(),
        _log_context(),
        logging.getLogger("test.retry.fallback"),
    )

    assert result.status is QuestionGenerationStatus.FALLBACK_USED
    assert result.question == "그때 마음이 어땠는지 말해 줄래?"
    assert result.attempts == 2
    assert result.failure_code is (
        QuestionGenerationFailureCode.PROVIDER_TEMPORARY_FAILURE
    )


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("error", "expected_code"),
    [
        (
            QuestionProviderError(
                QuestionProviderErrorCode.AUTHENTICATION_FAILED,
                retryable=False,
            ),
            QuestionGenerationFailureCode.PROVIDER_PERMANENT_FAILURE,
        ),
        (
            RuntimeError("PRIVATE_SDK_ERROR"),
            QuestionGenerationFailureCode.UNEXPECTED_PROVIDER_FAILURE,
        ),
    ],
)
async def test_non_retryable_failure_uses_fallback_immediately(
    error: BaseException,
    expected_code: QuestionGenerationFailureCode,
) -> None:
    provider = FakeProvider([error, "사용되면 안 되는 질문?"])

    result = await generate_question_with_fallback(
        _request(),
        provider,
        FakeValidator([]),
        _policy(),
        _log_context(),
        logging.getLogger("test.permanent.fallback"),
    )

    assert result.status is QuestionGenerationStatus.FALLBACK_USED
    assert result.attempts == 1
    assert result.failure_code is expected_code
    assert len(provider.prompts) == 1


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("retryable", "expected_attempts", "expected_code"),
    [
        (
            True,
            2,
            QuestionGenerationFailureCode.PROVIDER_TEMPORARY_FAILURE,
        ),
        (
            False,
            1,
            QuestionGenerationFailureCode.PROVIDER_PERMANENT_FAILURE,
        ),
    ],
)
async def test_validator_provider_error_follows_retry_contract(
    retryable: bool,
    expected_attempts: int,
    expected_code: QuestionGenerationFailureCode,
) -> None:
    provider = FakeProvider(["첫 질문?", "두 번째 질문?"])
    error = QuestionProviderError(
        QuestionProviderErrorCode.TEMPORARY_UNAVAILABLE,
        retryable=retryable,
    )
    validator = FakeValidator([error, error])

    result = await generate_question_with_fallback(
        _request(),
        provider,
        validator,
        _policy(),
        _log_context(),
        logging.getLogger("test.validator.provider.error"),
    )

    assert result.status is QuestionGenerationStatus.FALLBACK_USED
    assert result.attempts == expected_attempts
    assert result.failure_code is expected_code


@pytest.mark.asyncio
async def test_real_timeout_cleans_provider_before_retry_and_fallback() -> None:
    provider = BlockingProvider()

    result = await generate_question_with_fallback(
        _request(),
        provider,
        FakeValidator([]),
        _policy(attempts=1),
        _log_context(),
        logging.getLogger("test.real.timeout"),
    )

    assert result.status is QuestionGenerationStatus.FALLBACK_USED
    assert provider.started.is_set()
    assert provider.cleaned_up.is_set()


@pytest.mark.asyncio
async def test_outer_cancellation_is_propagated_and_cleans_provider() -> None:
    provider = BlockingProvider()
    task = asyncio.create_task(
        generate_question_with_fallback(
            _request(),
            provider,
            FakeValidator([]),
            QuestionGenerationExecutionPolicy(
                timeout_seconds=10,
                max_attempts=2,
            ),
            _log_context(),
            logging.getLogger("test.cancel"),
        )
    )
    await provider.started.wait()

    task.cancel()

    with pytest.raises(asyncio.CancelledError):
        await task
    assert provider.cleaned_up.is_set()


@pytest.mark.asyncio
async def test_outer_cancellation_cleans_validator_without_fallback() -> None:
    validator = BlockingValidator()
    task = asyncio.create_task(
        generate_question_with_fallback(
            _request(),
            FakeProvider(["후보 질문?"]),
            validator,
            QuestionGenerationExecutionPolicy(
                timeout_seconds=10,
                max_attempts=2,
            ),
            _log_context(),
            logging.getLogger("test.validator.cancel"),
        )
    )
    await validator.started.wait()

    task.cancel()

    with pytest.raises(asyncio.CancelledError):
        await task
    assert validator.cleaned_up.is_set()


@pytest.mark.asyncio
async def test_fallback_hard_rule_failure_returns_failed() -> None:
    fallback_text = "그때 마음이 어땠는지 말해 줄래?"
    provider = FakeProvider([RuntimeError("provider failed")])

    result = await generate_question_with_fallback(
        _request(recent_questions=(fallback_text,)),
        provider,
        FakeValidator([]),
        _policy(),
        _log_context(),
        logging.getLogger("test.fallback.failure"),
    )

    assert result.status is QuestionGenerationStatus.FAILED
    assert result.question is None
    assert result.failure_code is (
        QuestionGenerationFailureCode.FALLBACK_VALIDATION_FAILED
    )


@pytest.mark.asyncio
async def test_logs_only_safe_metadata(
    caplog: pytest.LogCaptureFixture,
) -> None:
    private_error = "PRIVATE_SDK_ERROR"
    private_candidate = "PRIVATE_GENERATED_QUESTION?"
    provider = FakeProvider(
        [private_candidate, RuntimeError(private_error)]
    )
    validator = FakeValidator([_invalid(private_candidate)])
    request = _request(
        recent_questions=("PRIVATE_RECENT_QUESTION?",)
    )

    with caplog.at_level(logging.WARNING, logger="test.safe.log"):
        result = await generate_question_with_fallback(
            request,
            provider,
            validator,
            _policy(),
            _log_context(),
            logging.getLogger("test.safe.log"),
        )

    assert result.status is QuestionGenerationStatus.FALLBACK_USED
    serialized_records = " ".join(
        repr(vars(record))
        for record in caplog.records
    )
    assert private_error not in serialized_records
    assert private_candidate not in serialized_records
    assert "PRIVATE_RECENT_QUESTION" not in serialized_records
    assert caplog.records[0].failure_stage == "validation"
    assert caplog.records[0].failure_code == "generatedQuestionRejected"
    assert caplog.records[1].failure_stage == "generation"
    assert caplog.records[1].failure_code == "unexpectedProviderFailure"
    fallback_record = caplog.records[2]
    assert fallback_record.conversation_id == "conversation-1"
    assert fallback_record.failure_stage == "fallback"
    assert fallback_record.failure_code == "unexpectedProviderFailure"
    assert fallback_record.fallback_used is True
