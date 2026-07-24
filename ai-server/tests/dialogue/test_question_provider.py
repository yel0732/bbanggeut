"""Tests for provider-neutral question-generation contracts."""

from dataclasses import FrozenInstanceError

import pytest

from app.dialogue import (
    QuestionProviderError,
    QuestionProviderErrorCode,
)


@pytest.mark.parametrize("code", list(QuestionProviderErrorCode))
@pytest.mark.parametrize("retryable", [True, False])
def test_provider_error_exposes_only_safe_contract(
    code: QuestionProviderErrorCode,
    retryable: bool,
) -> None:
    error = QuestionProviderError(code=code, retryable=retryable)

    assert str(error) == code.value
    assert error.args == (code.value,)
    assert "sdk" not in str(error).lower()


def test_provider_error_is_immutable() -> None:
    error = QuestionProviderError(
        QuestionProviderErrorCode.RATE_LIMITED,
        True,
    )

    with pytest.raises(FrozenInstanceError):
        error.retryable = False  # type: ignore[misc]


@pytest.mark.parametrize(
    ("code", "retryable"),
    [
        ("rateLimited", True),
        (QuestionProviderErrorCode.RATE_LIMITED, 1),
    ],
)
def test_rejects_invalid_provider_error_fields(
    code: object,
    retryable: object,
) -> None:
    with pytest.raises(ValueError):
        QuestionProviderError(  # type: ignore[arg-type]
            code=code,
            retryable=retryable,
        )
