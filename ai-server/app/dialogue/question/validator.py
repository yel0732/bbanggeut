"""Validate generated questions with hard rules and shared outcomes."""

import unicodedata
from dataclasses import dataclass
from enum import StrEnum
from typing import TYPE_CHECKING

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.dialogue.policy.target import QUESTION_TARGET_SLOT_FIELDS, QuestionTarget
from app.dialogue.schemas import DiarySlots

if TYPE_CHECKING:
    from app.dialogue.question.judge import SemanticQuestionValidationDecision


# Only expressions whose meaning does not depend on conversational context belong
# here. Semantic safety and leading-question checks are delegated to the LLM judge.
HARD_BANNED_QUESTION_EXPRESSIONS: tuple[str, ...] = (
    "빨리 말해",
    "반드시 대답해",
    "대답해야 해",
    "말해야 해",
    "틀렸어",
    "너는 문제가 있어",
    "왜 그랬",
    "왜 그런지",
    "왜 그랬는지",
    "기억에 남았구나",
    "기억에 남았네",
    "그 이야기에서 하나만 더",
    "잘 말해줬어",
    "나는 네 이야기를 듣고 있어",
    "내일 꼭",
    "내일은 반드시",
    "해야 해",
    "안 하면",
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
    allow_leading_statement: bool = False

    def __post_init__(self) -> None:
        if (
            isinstance(self.max_characters, bool)
            or not isinstance(self.max_characters, int)
            or self.max_characters < 1
        ):
            raise ValueError("max_characters must be a positive integer")
        if not isinstance(self.allow_leading_statement, bool):
            raise ValueError("allow_leading_statement must be a boolean")


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


_UNGROUNDED_TARGET_ASSERTION_CUES: dict[
    QuestionTarget,
    tuple[str, ...],
] = {
    QuestionTarget.EMOTION: (
        "기분이었구나",
        "마음이었구나",
    ),
    QuestionTarget.EMOTION_REASON: (
        "이유였구나",
        "이유였네",
        "때문이었구나",
        "때문이구나",
    ),
    QuestionTarget.MEMORABLE_MOMENT: (
        "기억에 남았구나",
        "기억에 남았네",
        "가장 기억에 남았구나",
        "제일 기억에 남았구나",
    ),
}

_CHILD_TARGET_EVIDENCE_CUES: dict[
    QuestionTarget,
    tuple[str, ...],
] = {
    QuestionTarget.EMOTION: ("기분", "마음"),
    QuestionTarget.EMOTION_REASON: ("이유", "때문"),
    QuestionTarget.MEMORABLE_MOMENT: ("기억", "기억에 남"),
}


def validate_target_assertion_grounding(
    *,
    target: QuestionTarget,
    question: str,
    current_slots: DiarySlots,
    child_answer: str | None,
) -> QuestionValidationResult:
    """Reject a response that claims the unanswered target is already known.

    The semantic judge remains useful for flexible language, but this narrow
    invariant must not depend on a probabilistic decision. A candidate may
    acknowledge target information when the latest child answer explicitly
    supplied it, even if slot extraction missed it.
    """

    normalized_question = question.strip()
    field_name = QUESTION_TARGET_SLOT_FIELDS[target]
    current_value = getattr(current_slots, field_name)
    if current_value is not None and current_value.strip():
        return QuestionValidationResult(
            normalized_question=normalized_question,
            issues=(),
        )

    assertion_cues = _UNGROUNDED_TARGET_ASSERTION_CUES.get(target, ())
    comparison_question = _normalize_for_comparison(normalized_question)
    asserts_unanswered_target = any(
        _normalize_for_comparison(cue) in comparison_question
        for cue in assertion_cues
    )
    if not asserts_unanswered_target:
        return QuestionValidationResult(
            normalized_question=normalized_question,
            issues=(),
        )

    comparison_answer = _normalize_for_comparison(child_answer or "")
    answer_evidence_cues = _CHILD_TARGET_EVIDENCE_CUES.get(target, ())
    latest_answer_supplied_target = any(
        _normalize_for_comparison(cue) in comparison_answer
        for cue in answer_evidence_cues
    )
    if latest_answer_supplied_target:
        return QuestionValidationResult(
            normalized_question=normalized_question,
            issues=(),
        )

    return QuestionValidationResult(
        normalized_question=normalized_question,
        issues=(
            QuestionValidationIssue(
                QuestionValidationCode.BANNED_EXPRESSION,
            ),
        ),
    )


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
    if not _has_exactly_one_question(
        question,
        allow_leading_statement=policy.allow_leading_statement,
    ):
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
    elif any(
        _semantic_question_bucket(question)
        == _semantic_question_bucket(recent_question)
        and _semantic_question_bucket(question) is not None
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


def validate_question_against_turn(
    *,
    target: QuestionTarget,
    question: str,
    current_question: str | None,
    child_answer: str | None,
) -> QuestionValidationResult:
    """Reject a target question already answered by the latest child turn."""

    normalized_question = question.strip()
    comparison_question = _normalize_for_comparison(normalized_question)
    comparison_current_question = _normalize_for_comparison(
        current_question or ""
    )
    comparison_answer = _normalize_for_comparison(child_answer or "")
    if _looks_like_child_question(comparison_answer):
        return QuestionValidationResult(
            normalized_question=normalized_question,
            issues=(),
        )
    issues: list[QuestionValidationIssue] = []

    if (
        target is QuestionTarget.EMOTION
        and _contains_emotion_cue(comparison_answer)
    ):
        issues.append(
            QuestionValidationIssue(QuestionValidationCode.TARGET_MISMATCH)
        )
    if (
        target is QuestionTarget.EMOTION_REASON
        and _answer_contains_emotion_reason(comparison_answer)
    ):
        issues.append(
            QuestionValidationIssue(QuestionValidationCode.TARGET_MISMATCH)
        )
    if (
        target in {QuestionTarget.EMOTION, QuestionTarget.EMOTION_REASON}
        and _asks_positive_moment(comparison_current_question)
        and _contains_answer_content(comparison_answer)
    ):
        issues.append(
            QuestionValidationIssue(QuestionValidationCode.TOO_SIMILAR)
        )
    if (
        _semantic_question_bucket(normalized_question) == "emotionReason"
        and _answer_contains_emotion_reason(comparison_answer)
    ):
        issues.append(
            QuestionValidationIssue(QuestionValidationCode.TOO_SIMILAR)
        )
    if "왜그랬" in comparison_question or "왜그런지" in comparison_question:
        issues.append(
            QuestionValidationIssue(QuestionValidationCode.BANNED_EXPRESSION)
        )
    if comparison_question.startswith("그랬구나"):
        issues.append(
            QuestionValidationIssue(QuestionValidationCode.BANNED_EXPRESSION)
        )

    return QuestionValidationResult(
        normalized_question=normalized_question,
        issues=tuple(dict.fromkeys(issues)),
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


def _has_exactly_one_question(
    question: str,
    *,
    allow_leading_statement: bool,
) -> bool:
    normalized = unicodedata.normalize("NFKC", question)
    if "\n" in normalized or "\r" in normalized:
        return False
    if normalized.count("?") != 1 or not normalized.endswith("?"):
        return False
    if allow_leading_statement:
        return True
    return not any(mark in normalized[:-1] for mark in (".", "!", "?", "。"))


def _normalize_for_comparison(text: str) -> str:
    normalized = unicodedata.normalize("NFKC", text).casefold()
    return "".join(character for character in normalized if character.isalnum())


def _contains_hard_banned_expression(comparison_text: str) -> bool:
    return any(
        _normalize_for_comparison(expression) in comparison_text
        for expression in HARD_BANNED_QUESTION_EXPRESSIONS
    )


def _semantic_question_bucket(question: str) -> str | None:
    normalized = _normalize_for_comparison(question)
    if any(
        cue in normalized
        for cue in (
            "무엇때문",
            "뭐때문",
            "어떤점때문",
            "어떤부분때문",
            "왜그런마음",
            "왜그렇게",
            "까닭",
        )
    ):
        return "emotionReason"
    if any(
        cue in normalized
        for cue in (
            "마음이어땠",
            "마음은어땠",
            "마음어땠",
            "기분이어땠",
            "기분은어땠",
            "기분어땠",
        )
    ):
        return "emotion"
    if any(
        cue in normalized
        for cue in (
            "또생각나는",
            "기억나는",
            "하고싶은말",
            "더말하고싶",
        )
    ):
        return "memorableMoment"
    if any(cue in normalized for cue in ("오늘있었던일", "오늘뭐했")):
        return "event"
    return None


def _contains_emotion_cue(comparison_text: str) -> bool:
    return any(
        cue in comparison_text
        for cue in (
            "재밌",
            "재미",
            "신기",
            "좋았",
            "좋아",
            "행복",
            "기뻤",
            "속상",
            "슬펐",
            "슬퍼",
            "화났",
            "화가",
            "아쉬",
            "무서",
        )
    )


def _answer_contains_emotion_reason(comparison_answer: str) -> bool:
    if not _contains_emotion_cue(comparison_answer):
        return False
    return any(
        connector in comparison_answer
        for connector in ("라서", "이라서", "해서", "때문", "니까", "는데")
    )


def _asks_positive_moment(comparison_question: str) -> bool:
    return any(
        cue in comparison_question
        for cue in (
            "어떤순간",
            "어떤모습",
            "제일재미",
            "제일좋",
            "뭐가그렇게",
        )
    )


def _contains_answer_content(comparison_answer: str) -> bool:
    return len(comparison_answer) >= 2


def _looks_like_child_question(comparison_answer: str) -> bool:
    return any(
        cue in comparison_answer
        for cue in (
            "너는",
            "너도",
            "좋아해",
            "싫어해",
            "해봤어",
            "먹어봤어",
            "가봤어",
            "어떻게",
            "뭐라고",
            "왜",
        )
    )
