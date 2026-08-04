"""Tests for deterministic turn understanding policy."""

from app.dialogue.policy.target import QuestionTarget
from app.dialogue.schemas import DiarySlots
from app.dialogue.turn_understanding import (
    apply_turn_understanding,
    understand_child_turn,
)


def test_fun_moment_answer_satisfies_emotion_and_reason() -> None:
    understanding = understand_child_turn(
        child_answer="손으로 내가 과일 자르는 거",
        current_question="오늘 과일 자르기 했구나! 어떤 순간이 제일 재미있었어?",
        asked_target=QuestionTarget.MEMORABLE_MOMENT,
        current_slots=DiarySlots(event="과일 자르기"),
    )

    slots = apply_turn_understanding(
        DiarySlots(event="과일 자르기"),
        understanding,
    )

    assert slots.emotion == "재미있었어"
    assert slots.emotion_reason == "손으로 내가 과일 자르는 거"
    assert slots.memorable_moment == "손으로 내가 과일 자르는 거"
    assert QuestionTarget.EMOTION in understanding.suppressed_targets
    assert QuestionTarget.EMOTION_REASON in understanding.suppressed_targets


def test_explicit_event_and_emotion_satisfies_reason() -> None:
    understanding = understand_child_turn(
        child_answer="과일이 잘리는데 너무 신기했어",
        current_question="오늘 과일 자르기 했구나! 어떤 순간이 제일 재미있었어?",
        asked_target=QuestionTarget.MEMORABLE_MOMENT,
        current_slots=DiarySlots(event="과일 자르기"),
    )

    slots = apply_turn_understanding(
        DiarySlots(event="과일 자르기"),
        understanding,
    )

    assert slots.emotion == "신기했어"
    assert slots.emotion_reason == "과일이 잘리는데"
    assert slots.memorable_moment == "과일이 잘리는데"


def test_continue_request_is_detected() -> None:
    understanding = understand_child_turn(
        child_answer="왜 마지막이야? 나랑 더 대화하자",
        current_question="마지막으로 나한테 하고 싶은 말 있어?",
        asked_target=QuestionTarget.MEMORABLE_MOMENT,
        current_slots=DiarySlots(),
    )

    assert understanding.child_question is True
    assert understanding.continue_request is True
