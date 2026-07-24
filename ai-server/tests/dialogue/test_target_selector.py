"""Tests for deterministic question target selection."""

import pytest

from app.dialogue import (
    QUESTION_TARGET_PRIORITY,
    QUESTION_TARGET_SLOT_FIELDS,
    DiarySlots,
    QuestionTarget,
    select_next_question_target,
)


def test_question_target_values_match_external_contract() -> None:
    assert [target.value for target in QuestionTarget] == [
        "event",
        "emotion",
        "emotionReason",
        "memorableMoment",
        "result",
    ]


def test_question_target_priority_matches_functional_spec() -> None:
    assert QUESTION_TARGET_PRIORITY == (
        QuestionTarget.EVENT,
        QuestionTarget.EMOTION,
        QuestionTarget.EMOTION_REASON,
        QuestionTarget.MEMORABLE_MOMENT,
        QuestionTarget.RESULT,
    )
    assert "people" not in QUESTION_TARGET_PRIORITY
    assert "place" not in QUESTION_TARGET_PRIORITY


def test_target_slot_mapping_is_the_single_priority_source() -> None:
    assert tuple(QUESTION_TARGET_SLOT_FIELDS) == QUESTION_TARGET_PRIORITY
    assert QUESTION_TARGET_SLOT_FIELDS == {
        QuestionTarget.EVENT: "event",
        QuestionTarget.EMOTION: "emotion",
        QuestionTarget.EMOTION_REASON: "emotion_reason",
        QuestionTarget.MEMORABLE_MOMENT: "memorable_moment",
        QuestionTarget.RESULT: "result",
    }

    with pytest.raises(TypeError):
        QUESTION_TARGET_SLOT_FIELDS[QuestionTarget.EVENT] = (  # type: ignore[index]
            "result"
        )


@pytest.mark.parametrize(
    ("slots", "expected"),
    [
        (DiarySlots(), QuestionTarget.EVENT),
        (
            DiarySlots(event="블록 놀이를 했다"),
            QuestionTarget.EMOTION,
        ),
        (
            DiarySlots(
                event="블록 놀이를 했다",
                emotion="신났다",
            ),
            QuestionTarget.EMOTION_REASON,
        ),
        (
            DiarySlots(
                event="블록 놀이를 했다",
                emotion="신났다",
                emotion_reason="성을 완성해서",
            ),
            QuestionTarget.MEMORABLE_MOMENT,
        ),
        (
            DiarySlots(
                event="블록 놀이를 했다",
                emotion="신났다",
                emotion_reason="성을 완성해서",
                memorable_moment="마지막 블록을 올린 순간",
            ),
            QuestionTarget.RESULT,
        ),
        (
            DiarySlots(
                event="블록 놀이를 했다",
                emotion="신났다",
                emotion_reason="성을 완성해서",
                memorable_moment="마지막 블록을 올린 순간",
                result="큰 성을 완성했다",
            ),
            None,
        ),
    ],
)
def test_selects_first_unfilled_target(
    slots: DiarySlots,
    expected: QuestionTarget | None,
) -> None:
    assert select_next_question_target(slots) is expected


def test_later_filled_slots_do_not_skip_earlier_empty_target() -> None:
    slots = DiarySlots(
        emotion="신났다",
        memorable_moment="친구와 웃은 순간",
        result="게임을 마쳤다",
    )

    assert select_next_question_target(slots) is QuestionTarget.EVENT


def test_people_and_place_do_not_change_target_priority() -> None:
    slots = DiarySlots(
        people=["친구"],
        place="유치원",
    )

    assert select_next_question_target(slots) is QuestionTarget.EVENT


def test_empty_people_and_place_do_not_prevent_no_target_result() -> None:
    slots = DiarySlots(
        event="블록 놀이를 했다",
        emotion="신났다",
        emotion_reason="성을 완성해서",
        memorable_moment="마지막 블록을 올린 순간",
        result="큰 성을 완성했다",
    )

    assert slots.people == []
    assert slots.place is None
    assert select_next_question_target(slots) is None


@pytest.mark.parametrize("empty_value", [None, "", "   "])
def test_treats_empty_text_as_unfilled(empty_value: str | None) -> None:
    slots = DiarySlots(
        event="블록 놀이를 했다",
        emotion=empty_value,
        emotion_reason="성을 완성해서",
    )

    assert select_next_question_target(slots) is QuestionTarget.EMOTION


def test_treats_text_with_outer_whitespace_as_filled() -> None:
    slots = DiarySlots(
        event="  블록 놀이를 했다  ",
    )

    assert select_next_question_target(slots) is QuestionTarget.EMOTION


def test_selection_does_not_mutate_slots() -> None:
    slots = DiarySlots(
        event="블록 놀이를 했다",
        people=["친구"],
    )
    before = slots.model_dump(mode="json", by_alias=True)

    select_next_question_target(slots)

    assert slots.model_dump(mode="json", by_alias=True) == before


def test_selection_is_deterministic() -> None:
    slots = DiarySlots(event="블록 놀이를 했다")

    first = select_next_question_target(slots)
    second = select_next_question_target(slots)

    assert first is QuestionTarget.EMOTION
    assert second is first


def test_excluded_target_is_skipped_without_changing_priority() -> None:
    excluded = {QuestionTarget.EVENT}

    selected = select_next_question_target(
        DiarySlots(),
        excluded_targets=excluded,
    )

    assert selected is QuestionTarget.EMOTION
    assert excluded == {QuestionTarget.EVENT}


def test_returns_none_when_every_unfilled_target_is_excluded() -> None:
    selected = select_next_question_target(
        DiarySlots(),
        excluded_targets=frozenset(QuestionTarget),
    )

    assert selected is None


def test_rejects_invalid_excluded_target() -> None:
    with pytest.raises(
        ValueError,
        match="excluded_targets must contain QuestionTarget values",
    ):
        select_next_question_target(
            DiarySlots(),
            excluded_targets={"event"},  # type: ignore[arg-type]
        )
