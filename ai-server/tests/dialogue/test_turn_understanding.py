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
    assert understanding.event == "오늘 1000점 맞았다"
    assert QuestionTarget.EVENT in understanding.satisfied_targets


def test_memorable_action_answer_is_kept_as_story_detail() -> None:
    understanding = understand_child_turn(
        child_answer="나 수박도 잘랐어!",
        current_question="그 이야기에서 하나만 더 들려줄래?",
        asked_target=QuestionTarget.MEMORABLE_MOMENT,
        current_slots=DiarySlots(event="과일 자르기"),
    )

    assert understanding.child_question is False
    assert understanding.event == "나 수박도 잘랐다"
    assert understanding.memorable_moment == "나 수박도 잘랐다"
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


def test_brag_question_tail_keeps_story_but_drops_tail() -> None:
    understanding = understand_child_turn(
        child_answer="내가 수박을 한 번에 여러 개 잘랐어. 짱이지?",
        current_question="그 게임에서 어떤 순간이 제일 재미있었어?",
        asked_target=QuestionTarget.MEMORABLE_MOMENT,
        current_slots=DiarySlots(event="과일 자르기 게임"),
    )

    assert understanding.child_question is True
    assert understanding.diary_content == "내가 수박을 한 번에 여러 개 잘랐어"
    assert understanding.memorable_moment == "내가 수박을 한 번에 여러 개 잘랐다"


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
    assert understanding.event == "놀이터에서 그네 못 타서 속상했다"
    assert understanding.emotion == "속상했어"
    assert understanding.emotion_reason == "놀이터에서 그네 못 타서"


def test_spoken_endings_are_stored_as_plain_statements() -> None:
    """슬롯은 계약대로 평서형으로 담긴다.

    아이 말투가 그대로 남으면 일기가 그 문장을 옮겨 적어 "학원에서 게임했어."처럼
    한 편 안에서 문체가 섞인다.
    """

    understanding = understand_child_turn(
        child_answer="놀이터에 갔어",
        current_question="오늘 무슨 일이 있었어?",
        asked_target=QuestionTarget.EVENT,
        current_slots=DiarySlots(),
    )

    assert understanding.event == "놀이터에 갔다"


def test_non_past_endings_are_left_untouched() -> None:
    """과거형이 아니면 손대지 않는다. 어설픈 변환이 비문을 만드는 것보다 낫다."""

    understanding = understand_child_turn(
        child_answer="그네를 타고 싶어",
        current_question="오늘 무슨 일이 있었어?",
        asked_target=QuestionTarget.EVENT,
        current_slots=DiarySlots(),
    )

    assert understanding.event == "그네를 타고 싶어"


def test_topic_refusal_without_story_fills_no_slot() -> None:
    """거부만 한 턴은 그날의 사건이 아니다.

    직전 질문 목표가 event였다는 이유로 거부 문장을 사건으로 받으면
    "게임 얘기 안 할래"가 일기와 리포트에 그날의 사건으로 실린다.
    """

    understanding = understand_child_turn(
        child_answer="게임 얘기 안 할래",
        current_question="오늘 과일 자르기 했구나! 어떤 일이 있었어?",
        asked_target=QuestionTarget.EVENT,
        current_slots=DiarySlots(event="과일 자르기 게임"),
    )

    assert understanding.topic_refusal is True
    assert understanding.event is None
    assert understanding.emotion is None
    assert understanding.memorable_moment is None
    assert not understanding.satisfied_targets


def test_negated_fun_is_not_read_as_enjoyment() -> None:
    """"재미없어"는 "재미"를 품고 있어 긍정 표를 먼저 보면 뜻이 뒤집힌다."""

    for answer in ["재미없어", "재미없었어", "안 재밌었어", "노잼이야"]:
        understanding = understand_child_turn(
            child_answer=answer,
            current_question="그때 마음은 어땠어?",
            asked_target=QuestionTarget.EMOTION,
            current_slots=DiarySlots(event="과일 자르기"),
        )

        assert understanding.emotion == "재미없었어", answer


def test_refusal_without_story_ignores_a_fun_moment_question() -> None:
    """이야기를 꺼내지 않은 거부에는 감정도 생기지 않는다.

    끝난 주제의 "어떤 점이 재미있었어?"를 근거로 감정을 채우면 전환 인사가
    "오늘 그런 일이 있었구나"로 나간다 — 아이는 아무 이야기도 하지 않았다.
    """

    understanding = understand_child_turn(
        child_answer="게임 얘기 안 할래",
        current_question="어떤 점이 재미있었어?",
        asked_target=QuestionTarget.EMOTION_REASON,
        current_slots=DiarySlots(event="과일 자르기 게임"),
    )

    assert understanding.topic_refusal is True
    assert understanding.emotion is None
    assert understanding.emotion_reason is None
    assert understanding.memorable_moment is None
    assert not understanding.satisfied_targets


def test_topic_refusal_ignores_finished_topic_question() -> None:
    """끝난 주제의 질문이 새 주제 슬롯을 채우면 안 된다.

    직전 질문이 감정을 묻고 있었다는 이유로 감정을 채우면, 아이가 하지 않은 말이
    들어가고 일상 주제가 시작하자마자 필수 목표가 다 찬 것으로 보인다.
    """

    understanding = understand_child_turn(
        child_answer="게임 얘기 안 할래. 오늘 할머니 생일이었어",
        current_question="게임에서 어떤 점이 재미있었어?",
        asked_target=QuestionTarget.EMOTION_REASON,
        current_slots=DiarySlots(event="과일 자르기 게임"),
    )

    assert understanding.topic_refusal is True
    # 동작 동사가 없어도 아이가 먼저 꺼낸 이야기는 사건으로 받는다.
    assert understanding.event == "오늘 할머니 생일이었다"
    assert understanding.emotion is None
    assert understanding.emotion_reason is None
    assert understanding.memorable_moment is None
    assert QuestionTarget.EMOTION not in understanding.satisfied_targets
