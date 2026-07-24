"""Tests for T14 execution values and trusted fallback questions."""

from dataclasses import FrozenInstanceError

import pytest

from app.dialogue import (
    SIMPLIFIED_QUESTION_BY_TARGET,
    FallbackQuestion,
    QuestionGenerationExecutionPolicy,
    QuestionGenerationExecutionResult,
    QuestionGenerationFailureCode,
    QuestionGenerationLogContext,
    QuestionGenerationSource,
    QuestionGenerationStatus,
    QuestionRuleValidationRequest,
    QuestionTarget,
    build_fallback_question,
    validate_question_rules,
)


@pytest.mark.parametrize("target", list(QuestionTarget))
def test_fallback_reuses_t13_mapping_and_passes_hard_rules(
    target: QuestionTarget,
) -> None:
    fallback = build_fallback_question(target)

    assert fallback.text == SIMPLIFIED_QUESTION_BY_TARGET[target]
    assert validate_question_rules(
        QuestionRuleValidationRequest(question=fallback.text)
    ).is_valid


def test_fallback_rejects_unregistered_text_and_hides_text_from_repr() -> None:
    fallback = build_fallback_question(QuestionTarget.EVENT)

    assert fallback.text not in repr(fallback)
    with pytest.raises(ValueError):
        FallbackQuestion(
            target=QuestionTarget.EVENT,
            text="임의 질문은 사용할 수 있을까?",
        )


@pytest.mark.parametrize(
    "timeout_seconds",
    [0, -1, True, float("inf"), float("-inf"), float("nan"), "1"],
)
def test_policy_rejects_invalid_timeout(timeout_seconds: object) -> None:
    with pytest.raises(ValueError):
        QuestionGenerationExecutionPolicy(  # type: ignore[arg-type]
            timeout_seconds=timeout_seconds,
            max_attempts=1,
        )


@pytest.mark.parametrize("max_attempts", [0, -1, True, 1.5, "1"])
def test_policy_rejects_invalid_attempt_count(max_attempts: object) -> None:
    with pytest.raises(ValueError):
        QuestionGenerationExecutionPolicy(  # type: ignore[arg-type]
            timeout_seconds=1,
            max_attempts=max_attempts,
        )


def test_policy_is_immutable() -> None:
    policy = QuestionGenerationExecutionPolicy(
        timeout_seconds=1,
        max_attempts=2,
    )

    with pytest.raises(FrozenInstanceError):
        policy.max_attempts = 3  # type: ignore[misc]


def test_result_accepts_each_valid_state_and_hides_question() -> None:
    private_question = "PRIVATE_GENERATED_QUESTION?"
    generated = QuestionGenerationExecutionResult(
        status=QuestionGenerationStatus.GENERATED,
        source=QuestionGenerationSource.LLM,
        question=private_question,
        attempts=1,
        failure_code=None,
    )
    fallback = QuestionGenerationExecutionResult(
        status=QuestionGenerationStatus.FALLBACK_USED,
        source=QuestionGenerationSource.RULE_BASED_FALLBACK,
        question=private_question,
        attempts=2,
        failure_code=QuestionGenerationFailureCode.TIMEOUT,
    )
    failed = QuestionGenerationExecutionResult(
        status=QuestionGenerationStatus.FAILED,
        source=None,
        question=None,
        attempts=2,
        failure_code=(
            QuestionGenerationFailureCode.FALLBACK_VALIDATION_FAILED
        ),
    )

    assert private_question not in repr(generated)
    assert private_question not in repr(fallback)
    assert failed.question is None


@pytest.mark.parametrize(
    "result",
    [
        {
            "status": QuestionGenerationStatus.GENERATED,
            "source": QuestionGenerationSource.LLM,
            "question": "질문?",
            "attempts": 1,
            "failure_code": QuestionGenerationFailureCode.TIMEOUT,
        },
        {
            "status": QuestionGenerationStatus.FALLBACK_USED,
            "source": QuestionGenerationSource.LLM,
            "question": "질문?",
            "attempts": 1,
            "failure_code": QuestionGenerationFailureCode.TIMEOUT,
        },
        {
            "status": QuestionGenerationStatus.FAILED,
            "source": None,
            "question": "질문?",
            "attempts": 1,
            "failure_code": (
                QuestionGenerationFailureCode.FALLBACK_VALIDATION_FAILED
            ),
        },
    ],
)
def test_result_rejects_inconsistent_states(result: dict[str, object]) -> None:
    with pytest.raises(ValueError):
        QuestionGenerationExecutionResult(**result)  # type: ignore[arg-type]


@pytest.mark.parametrize(
    "values",
    [
        ("", None, None, "fake"),
        ("conversation", "", None, "fake"),
        ("conversation", None, " ", "fake"),
        ("conversation", None, None, ""),
    ],
)
def test_log_context_rejects_blank_identifiers(
    values: tuple[str, str | None, str | None, str],
) -> None:
    with pytest.raises(ValueError):
        QuestionGenerationLogContext(*values)
