"""Tests for validated diary slot merging."""

import pytest
from pydantic import ValidationError

from app.dialogue import DiarySlots, merge_diary_slots


def test_missing_extraction_values_do_not_erase_current_slots() -> None:
    current = DiarySlots(
        event="블록 놀이를 했다",
        people=["친구"],
        place="유치원",
        emotion="신났다",
    )

    merged = merge_diary_slots(current, DiarySlots())

    assert merged == current
    assert merged is not current


def test_confirmed_scalar_updates_existing_value() -> None:
    current = DiarySlots(event="놀이를 했다", emotion="즐거웠다")
    extracted = DiarySlots(
        event="친구와 블록 성을 만들었다",
        emotion_reason="성을 완성해서",
    )

    merged = merge_diary_slots(current, extracted)

    assert merged.event == "친구와 블록 성을 만들었다"
    assert merged.emotion == "즐거웠다"
    assert merged.emotion_reason == "성을 완성해서"


def test_blank_scalar_does_not_erase_existing_value() -> None:
    current = DiarySlots(event="그림을 그렸다")
    extracted = DiarySlots(event="   ")

    merged = merge_diary_slots(current, extracted)

    assert merged.event == "그림을 그렸다"


def test_new_scalar_is_trimmed() -> None:
    merged = merge_diary_slots(
        DiarySlots(),
        DiarySlots(memorable_moment="  그림을 보여 준 순간  "),
    )

    assert merged.memorable_moment == "그림을 보여 준 순간"


def test_people_are_merged_in_stable_order_and_deduplicated() -> None:
    current = DiarySlots(people=["친구", " 선생님 "])
    extracted = DiarySlots(people=["친구", " 가족 ", "", "  "])

    merged = merge_diary_slots(current, extracted)

    assert merged.people == ["친구", "선생님", "가족"]


def test_merge_does_not_mutate_inputs() -> None:
    current = DiarySlots(event="놀이", people=["친구"])
    extracted = DiarySlots(event="그림", people=["가족"])
    current_before = current.model_dump(mode="json", by_alias=True)
    extracted_before = extracted.model_dump(mode="json", by_alias=True)

    merge_diary_slots(current, extracted)

    assert current.model_dump(mode="json", by_alias=True) == current_before
    assert extracted.model_dump(mode="json", by_alias=True) == extracted_before


def test_revalidates_mutated_nested_values_before_merge() -> None:
    private_value = "PRIVATE_CHILD_DIARY_VALUE"
    current = DiarySlots(people=["친구"])
    current.people.append({"name": private_value})  # type: ignore[arg-type]

    with pytest.raises(ValidationError) as caught:
        merge_diary_slots(current, DiarySlots())

    assert private_value not in str(caught.value)
