"""Tests for deterministic generated-question hard rules."""

from dataclasses import FrozenInstanceError

import pytest
from pydantic import ValidationError

from app.dialogue import (
    DEFAULT_QUESTION_RULE_VALIDATION_POLICY,
    HARD_BANNED_QUESTION_EXPRESSIONS,
    QuestionRuleValidationPolicy,
    QuestionRuleValidationRequest,
    QuestionValidationCode,
    SemanticQuestionValidationDecision,
    combine_question_validation_results,
    validate_question_rules,
)


def _validate(
    question: str,
    *,
    recent_questions: tuple[str, ...] = (),
    policy: QuestionRuleValidationPolicy = (
        DEFAULT_QUESTION_RULE_VALIDATION_POLICY
    ),
):
    request = QuestionRuleValidationRequest(
        question=question,
        recent_questions=recent_questions,
    )
    return validate_question_rules(request, policy)


def test_accepts_valid_question_and_strips_outer_whitespace() -> None:
    result = _validate("  그때 어떤 기분이었어?  ")

    assert result.is_valid
    assert result.normalized_question == "그때 어떤 기분이었어?"
    assert result.issues == ()


def test_accepts_camel_case_request_and_normalizes_recent_questions() -> None:
    request = QuestionRuleValidationRequest.model_validate(
        {
            "question": "오늘 무슨 일이 있었어?",
            "recentQuestions": ["  전에 무엇을 했어?  "],
        }
    )

    assert request.recent_questions == ("전에 무엇을 했어?",)


def test_rejects_unknown_request_field() -> None:
    with pytest.raises(ValidationError):
        QuestionRuleValidationRequest.model_validate(
            {
                "question": "오늘 무슨 일이 있었어?",
                "target": "event",
            }
        )


@pytest.mark.parametrize("blank_question", ["", "   "])
def test_rejects_blank_recent_question(blank_question: str) -> None:
    with pytest.raises(ValidationError):
        QuestionRuleValidationRequest(
            question="오늘 무슨 일이 있었어?",
            recent_questions=(blank_question,),
        )


def test_request_and_result_repr_hide_question_text() -> None:
    private_question = "PRIVATE_CURRENT_QUESTION"
    request = QuestionRuleValidationRequest(question=private_question)
    result = validate_question_rules(request)

    assert private_question not in repr(request)
    assert private_question not in repr(result)


def test_validation_error_hides_input() -> None:
    private_question = "PRIVATE_CURRENT_QUESTION"

    with pytest.raises(ValidationError) as caught:
        QuestionRuleValidationRequest.model_validate(
            {
                "question": private_question,
                "recentQuestions": [123],
            }
        )

    assert private_question not in str(caught.value)


def test_default_policy_contract_and_immutability() -> None:
    policy = DEFAULT_QUESTION_RULE_VALIDATION_POLICY

    assert policy.max_characters == 50
    with pytest.raises(FrozenInstanceError):
        policy.max_characters = 10  # type: ignore[misc]


@pytest.mark.parametrize("max_characters", [0, -1, True, 1.5, "50"])
def test_rejects_invalid_max_characters(max_characters: object) -> None:
    with pytest.raises(ValueError):
        QuestionRuleValidationPolicy(  # type: ignore[arg-type]
            max_characters=max_characters
        )


@pytest.mark.parametrize("question", ["", "   "])
def test_reports_only_empty_question(question: str) -> None:
    result = _validate(question)

    assert not result.is_valid
    assert result.normalized_question == ""
    assert tuple(issue.code for issue in result.issues) == (
        QuestionValidationCode.EMPTY_QUESTION,
    )


@pytest.mark.parametrize(
    "question",
    [
        "어떤 기분이었어",
        "무슨 일이 있었어? 기분은 어땠어?",
        "정말 잘했네. 어떤 기분이었어?",
        "정말 잘했네。어떤 기분이었어?",
        "어떤 기분이었어?\n다른 것도 말해 줘.",
    ],
)
def test_rejects_unclear_question_form(question: str) -> None:
    result = _validate(question)

    assert QuestionValidationCode.INVALID_QUESTION_COUNT in {
        issue.code for issue in result.issues
    }


def test_accepts_one_full_width_question_mark() -> None:
    assert _validate("그때 어떤 기분이었어？").is_valid


def test_applies_length_boundary_after_outer_strip() -> None:
    accepted = ("가" * 49) + "?"
    rejected = ("가" * 50) + "?"

    assert _validate(f" {accepted} ").is_valid
    assert QuestionValidationCode.TOO_LONG in {
        issue.code for issue in _validate(rejected).issues
    }


@pytest.mark.parametrize(
    "question",
    [
        "빨리 말해, 무슨 일이 있었어?",
        "반드시 대답해, 오늘 무엇을 했어?",
        "대답해야 해, 오늘 무엇을 했어?",
        "틀 렸 어, 그다음에는 무엇을 했어?",
        "너는 문제가 있어, 오늘 무슨 일이 있었어?",
    ],
)
def test_rejects_unambiguous_hard_banned_expression(question: str) -> None:
    result = _validate(question)

    assert QuestionValidationCode.BANNED_EXPRESSION in {
        issue.code for issue in result.issues
    }


@pytest.mark.parametrize(
    "question",
    [
        "어떤 점이 재미있었어?",
        "그때 신났는지 알려 줄래?",
        "어떤 점 때문에 슬펐어?",
        "무서웠던 순간은 언제였어?",
        "정답을 맞혔을 때 어떤 기분이었어?",
        "이 질문에 대답해 줄래?",
        "오늘 어떤 문제가 있었어?",
    ],
)
def test_does_not_hard_reject_context_dependent_question(
    question: str,
) -> None:
    assert _validate(question).is_valid


def test_hard_banned_expression_collection_is_immutable() -> None:
    assert isinstance(HARD_BANNED_QUESTION_EXPRESSIONS, tuple)
    with pytest.raises(TypeError):
        HARD_BANNED_QUESTION_EXPRESSIONS[0] = "변경"  # type: ignore[index]


def test_rejects_exact_duplicate_after_normalization() -> None:
    result = _validate(
        "그때 어떤 기분이었어?",
        recent_questions=("그때어떤기분이었어！",),
    )

    assert QuestionValidationCode.TOO_SIMILAR in {
        issue.code for issue in result.issues
    }


def test_leaves_semantic_rephrasing_to_judge() -> None:
    result = _validate(
        "그때 어떤 기분이었어?",
        recent_questions=("그때 기분은 어땠어?",),
    )

    assert result.is_valid


def test_returns_multiple_hard_issues_in_stable_order() -> None:
    question = "빨리 말해. 오늘 무슨 일이 있었어?"
    result = _validate(
        question,
        recent_questions=(question,),
        policy=QuestionRuleValidationPolicy(max_characters=5),
    )

    assert tuple(issue.code for issue in result.issues) == (
        QuestionValidationCode.INVALID_QUESTION_COUNT,
        QuestionValidationCode.TOO_LONG,
        QuestionValidationCode.BANNED_EXPRESSION,
        QuestionValidationCode.TOO_SIMILAR,
    )


def test_validation_is_deterministic_and_does_not_mutate_request() -> None:
    request = QuestionRuleValidationRequest(
        question="  그때 어떤 기분이었어?  ",
        recent_questions=("오늘 무슨 일이 있었어?",),
    )
    before = request.model_dump(mode="json", by_alias=True)

    assert validate_question_rules(request) == validate_question_rules(request)
    assert request.model_dump(mode="json", by_alias=True) == before


def test_hard_failure_wins_without_semantic_decision() -> None:
    rule_result = _validate("")

    combined = combine_question_validation_results(rule_result, None)

    assert combined.issues == rule_result.issues


def test_combines_valid_semantic_decision() -> None:
    rule_result = _validate("오늘 무슨 일이 있었어?")
    decision = SemanticQuestionValidationDecision(valid=True, issues=())

    assert combine_question_validation_results(rule_result, decision).is_valid


def test_combines_invalid_semantic_decision_and_hides_question() -> None:
    rule_result = _validate("오늘 무슨 일이 있었어?")
    decision = SemanticQuestionValidationDecision(
        valid=False,
        issues=(QuestionValidationCode.TARGET_MISMATCH,),
    )

    combined = combine_question_validation_results(rule_result, decision)

    assert tuple(issue.code for issue in combined.issues) == (
        QuestionValidationCode.TARGET_MISMATCH,
    )
    assert rule_result.normalized_question not in repr(combined)


def test_requires_semantic_decision_after_hard_rules_pass() -> None:
    with pytest.raises(ValueError):
        combine_question_validation_results(
            _validate("오늘 무슨 일이 있었어?"),
            None,
        )
