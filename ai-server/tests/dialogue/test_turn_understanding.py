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


def test_child_question_does_not_block_diary_content() -> None:
    understanding = understand_child_turn(
        child_answer="너도 게임 해봤어? 오늘 1000점 맞았어",
        current_question="오늘 게임에서 어떤 일이 있었어?",
        asked_target=QuestionTarget.EVENT,
        current_slots=DiarySlots(),
    )

    assert understanding.child_question is True
    assert understanding.event == "오늘 1000점 맞았어"
    assert QuestionTarget.EVENT in understanding.satisfied_targets


def test_memorable_action_answer_is_kept_as_story_detail() -> None:
    understanding = understand_child_turn(
        child_answer="나 수박도 잘랐어!",
        current_question="그 이야기에서 하나만 더 들려줄래?",
        asked_target=QuestionTarget.MEMORABLE_MOMENT,
        current_slots=DiarySlots(event="과일 자르기"),
    )

    assert understanding.child_question is False
    assert understanding.event == "나 수박도 잘랐어"
    assert understanding.memorable_moment == "나 수박도 잘랐어"
    assert QuestionTarget.MEMORABLE_MOMENT in understanding.satisfied_targets


def test_child_question_tail_is_not_stored_as_diary_content() -> None:
    understanding = understand_child_turn(
        child_answer="너도 해본 적 있어? 이 게임?",
        current_question="그 이야기에서 하나만 더 들려줄래?",
        asked_target=QuestionTarget.MEMORABLE_MOMENT,
        current_slots=DiarySlots(event="과일 자르기"),
    )

    assert understanding.child_question is True
    assert understanding.diary_content is None
    assert understanding.event is None


def test_topic_refusal_keeps_following_daily_story() -> None:
    understanding = understand_child_turn(
        child_answer=(
            "게임 얘기 안 할래. 놀이터에서 그네 못 타서 속상했어"
        ),
        current_question="게임에서 어떤 일이 있었어?",
        asked_target=QuestionTarget.EVENT,
        current_slots=DiarySlots(),
    )

    assert understanding.topic_refusal is True
    assert understanding.event == "놀이터에서 그네 못 타서 속상했어"
    assert understanding.emotion == "속상했어"
    assert understanding.emotion_reason == "놀이터에서 그네 못 타서"
