"""Tests for structured LLM dialogue-turn output parsing."""

import json

import pytest

from app.dialogue.policy.target import QuestionTarget
from app.dialogue.question.candidate import (
    GeneratedDialogueTurnParseError,
    GeneratedDialogueTurnParseErrorCode,
    parse_generated_dialogue_turn,
)


def _payload(**updates: object) -> str:
    payload: dict[str, object] = {
        "acknowledgement": "여러 개를 한 번에 자르는 게 재미있었구나!",
        "bridge": None,
        "question": "그 순간에는 어떤 기분이 들었어?",
        "target": "emotion",
        "acknowledgementEvidence": {
            "source": "childAnswer",
            "text": "여러개 자르는게 재미있었어",
        },
    }
    payload.update(updates)
    return json.dumps(payload, ensure_ascii=False)


def test_parses_structured_candidate_and_combines_components() -> None:
    candidate = parse_generated_dialogue_turn(
        _payload(),
        expected_target=QuestionTarget.EMOTION,
        child_answer="과일을 한번에 여러개 자르는게 재미있었어",
    )

    assert candidate.structured is True
    assert candidate.bridge is None
    assert candidate.question == "그 순간에는 어떤 기분이 들었어?"
    assert candidate.message == (
        "여러 개를 한 번에 자르는 게 재미있었구나! "
        "그 순간에는 어떤 기분이 들었어?"
    )


@pytest.mark.parametrize(
    ("raw_output", "expected_code"),
    [
        (
            _payload(target="memorableMoment"),
            GeneratedDialogueTurnParseErrorCode.TARGET_MISMATCH,
        ),
        (
            _payload(
                acknowledgementEvidence={
                    "source": "childAnswer",
                    "text": "아이에게 없는 근거",
                }
            ),
            GeneratedDialogueTurnParseErrorCode.EVIDENCE_MISMATCH,
        ),
        (
            '{"acknowledgement":"반응","question":"질문?"}',
            GeneratedDialogueTurnParseErrorCode.SCHEMA_VALIDATION_FAILED,
        ),
        (
            '{"acknowledgement":"반응",',
            GeneratedDialogueTurnParseErrorCode.INVALID_JSON,
        ),
    ],
)
def test_rejects_untrusted_structured_output(
    raw_output: str,
    expected_code: GeneratedDialogueTurnParseErrorCode,
) -> None:
    with pytest.raises(GeneratedDialogueTurnParseError) as caught:
        parse_generated_dialogue_turn(
            raw_output,
            expected_target=QuestionTarget.EMOTION,
            child_answer="과일을 여러 개 자르는 게 재미있었어",
        )

    assert caught.value.code is expected_code


def test_keeps_plain_text_compatibility_during_rollout() -> None:
    candidate = parse_generated_dialogue_turn(
        "재미있었구나! 그때 어떤 기분이 들었어?",
        expected_target=QuestionTarget.EMOTION,
        child_answer="재미있었어",
    )

    assert candidate.structured is False
    assert candidate.acknowledgement == "재미있었구나!"
    assert candidate.bridge is None
    assert candidate.question == "그때 어떤 기분이 들었어?"


def test_requires_structured_bridge_for_redirect_generation() -> None:
    with pytest.raises(GeneratedDialogueTurnParseError) as caught:
        parse_generated_dialogue_turn(
            _payload(),
            expected_target=QuestionTarget.EMOTION,
            child_answer="과일을 한번에 여러개 자르는게 재미있었어",
            bridge_required=True,
        )

    assert caught.value.code is (
        GeneratedDialogueTurnParseErrorCode.MISSING_BRIDGE
    )
