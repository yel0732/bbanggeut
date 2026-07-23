"""Hard rules and shared outcomes for generated-question validation."""

import unicodedata
from dataclasses import dataclass
from enum import StrEnum
from typing import TYPE_CHECKING

from pydantic import BaseModel, ConfigDict, Field, field_validator

if TYPE_CHECKING:
    from app.dialogue.question_judge import SemanticQuestionValidationDecision


# Only expressions whose meaning does not depend on conversational context belong
# here. Semantic safety and leading-question checks are delegated to the LLM judge.
HARD_BANNED_QUESTION_EXPRESSIONS: tuple[str, ...] = (
    "빨리 말해",
    "반드시 대답해",
    "대답해야 해",
    "말해야 해",
    "틀렸어",
    "너는 문제가 있어",
)


class QuestionRuleValidationRequest(BaseModel):
    """Minimum context required for deterministic hard-rule validation."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        validate_assignment=True,
    )

    question: str = Field(repr=False)
    recent_questions: tuple[str, ...] = Field(
        default_factory=tuple,
        alias="recentQuestions",
        repr=False,
    )

    @field_validator("recent_questions")
    @classmethod
    def normalize_recent_questions(
        cls,
        value: tuple[str, ...],
    ) -> tuple[str, ...]:
        """Strip recent questions and reject entries without content."""

        normalized = tuple(question.strip() for question in value)
        if any(not question for question in normalized):
            raise ValueError("recent questions must contain non-whitespace text")
        return normalized


@dataclass(frozen=True)
class QuestionRuleValidationPolicy:
    """Limits that can be decided without interpreting question meaning."""

    max_characters: int = 50

    def __post_init__(self) -> None:
        if (
            isinstance(self.max_characters, bool)
            or not isinstance(self.max_characters, int)
            or self.max_characters < 1
        ):
            raise ValueError("max_characters must be a positive integer")


DEFAULT_QUESTION_RULE_VALIDATION_POLICY = QuestionRuleValidationPolicy()


class QuestionValidationCode(StrEnum):
    """Safe reasons why a generated question cannot be used."""

    EMPTY_QUESTION = "emptyQuestion"
    INVALID_QUESTION_COUNT = "invalidQuestionCount"
    TOO_LONG = "tooLong"
    BANNED_EXPRESSION = "bannedExpression"
    TOO_SIMILAR = "tooSimilar"
    TARGET_MISMATCH = "targetMismatch"


@dataclass(frozen=True)
class QuestionValidationIssue:
    """One safe validation failure without generated text."""

    code: QuestionValidationCode


@dataclass(frozen=True, repr=False)
class QuestionRuleValidationResult:
    """Immutable outcome produced by deterministic hard rules."""

    normalized_question: str
    issues: tuple[QuestionValidationIssue, ...]

    @property
    def is_valid(self) -> bool:
        return not self.issues


@dataclass(frozen=True, repr=False)
class QuestionValidationResult:
    """Final outcome after hard-rule and semantic validation."""

    normalized_question: str
    issues: tuple[QuestionValidationIssue, ...]

    @property
    def is_valid(self) -> bool:
        return not self.issues


def validate_question_rules(
    request: QuestionRuleValidationRequest,
    policy: QuestionRuleValidationPolicy = (
        DEFAULT_QUESTION_RULE_VALIDATION_POLICY
    ),
) -> QuestionRuleValidationResult:
    """Apply only deterministic rules that do not infer question meaning."""

    question = request.question.strip()
    if not question:
        return QuestionRuleValidationResult(
            normalized_question="",
            issues=(
                QuestionValidationIssue(
                    QuestionValidationCode.EMPTY_QUESTION,
                ),
            ),
        )

    issues: list[QuestionValidationIssue] = []
    if not _has_exactly_one_question(question):
        issues.append(
            QuestionValidationIssue(
                QuestionValidationCode.INVALID_QUESTION_COUNT,
            )
        )
    if len(question) > policy.max_characters:
        issues.append(
            QuestionValidationIssue(
                QuestionValidationCode.TOO_LONG,
            )
        )

    comparison_text = _normalize_for_comparison(question)
    if _contains_hard_banned_expression(comparison_text):
        issues.append(
            QuestionValidationIssue(
                QuestionValidationCode.BANNED_EXPRESSION,
            )
        )
    if any(
        comparison_text == _normalize_for_comparison(recent_question)
        for recent_question in request.recent_questions
    ):
        issues.append(
            QuestionValidationIssue(
                QuestionValidationCode.TOO_SIMILAR,
            )
        )

    return QuestionRuleValidationResult(
        normalized_question=question,
        issues=tuple(issues),
    )


def combine_question_validation_results(
    rule_result: QuestionRuleValidationResult,
    semantic_decision: "SemanticQuestionValidationDecision | None",
) -> QuestionValidationResult:
    """Combine validation stages while giving hard-rule failures priority."""

    if not rule_result.is_valid:
        return QuestionValidationResult(
            normalized_question=rule_result.normalized_question,
            issues=rule_result.issues,
        )
    if semantic_decision is None:
        raise ValueError(
            "semantic_decision is required when hard-rule validation passes"
        )

    return QuestionValidationResult(
        normalized_question=rule_result.normalized_question,
        issues=tuple(
            QuestionValidationIssue(code)
            for code in semantic_decision.issues
        ),
    )


def _has_exactly_one_question(question: str) -> bool:
    normalized = unicodedata.normalize("NFKC", question)
    if "\n" in normalized or "\r" in normalized:
        return False
    if normalized.count("?") != 1 or not normalized.endswith("?"):
        return False
    return not any(mark in normalized[:-1] for mark in (".", "!", "?", "。"))


def _normalize_for_comparison(text: str) -> str:
    normalized = unicodedata.normalize("NFKC", text).casefold()
    return "".join(character for character in normalized if character.isalnum())


def _contains_hard_banned_expression(comparison_text: str) -> bool:
    return any(
        _normalize_for_comparison(expression) in comparison_text
        for expression in HARD_BANNED_QUESTION_EXPRESSIONS
    )
