"""Tests for strict diary slot output parsing."""

import pytest

from app.dialogue import (
    DiarySlots,
    SlotParseError,
    SlotParseErrorCode,
    parse_diary_slots,
)


def test_parses_valid_json_output() -> None:
    raw_output = """
    {
      "event": "블록 성을 만들었다",
      "people": ["친구"],
      "place": null,
      "emotion": "신났다",
      "emotionReason": null,
      "memorableMoment": null
    }
    """

    slots = parse_diary_slots(raw_output)

    assert slots.event == "블록 성을 만들었다"
    assert slots.people == ["친구"]
    assert slots.emotion == "신났다"


def test_parses_structured_mapping_output() -> None:
    slots = parse_diary_slots(
        {
            "event": "그림을 그렸다",
            "memorableMoment": "가족에게 보여 준 순간",
        }
    )

    assert slots == DiarySlots(
        event="그림을 그렸다",
        memorable_moment="가족에게 보여 준 순간",
    )


def test_partial_output_keeps_unknown_defaults() -> None:
    slots = parse_diary_slots('{"emotion":"즐거웠어"}')

    assert slots.model_dump(mode="json", by_alias=True) == {
        "event": None,
        "people": [],
        "place": None,
        "emotion": "즐거웠어",
        "emotionReason": None,
        "memorableMoment": None,
    }


@pytest.mark.parametrize(
    "payload",
    [
        {"emotion_reason": "성을 완성해서"},
        {"memorable_moment": "성을 완성한 순간"},
    ],
)
def test_rejects_snake_case_provider_fields(
    payload: dict[str, object],
) -> None:
    with pytest.raises(SlotParseError) as caught:
        parse_diary_slots(payload)

    assert caught.value.code is SlotParseErrorCode.SCHEMA_VALIDATION_FAILED


@pytest.mark.parametrize("raw_output", ["", "   "])
def test_rejects_empty_output(raw_output: str) -> None:
    with pytest.raises(SlotParseError) as caught:
        parse_diary_slots(raw_output)

    assert caught.value.code is SlotParseErrorCode.EMPTY_OUTPUT
    assert caught.value.recoverable is True


@pytest.mark.parametrize(
    "raw_output",
    [
        '{"event":',
        '```json\n{"event":"놀이"}\n```',
        '{"event":"첫 번째","event":"두 번째"}',
    ],
)
def test_rejects_invalid_json_without_text_repair(raw_output: str) -> None:
    with pytest.raises(SlotParseError) as caught:
        parse_diary_slots(raw_output)

    assert caught.value.code is SlotParseErrorCode.INVALID_JSON
    assert raw_output not in str(caught.value)


@pytest.mark.parametrize(
    "raw_output",
    [
        "[]",
        '"event"',
        "1",
        ["not", "a", "mapping"],
    ],
)
def test_rejects_non_object_root(raw_output: object) -> None:
    with pytest.raises(SlotParseError) as caught:
        parse_diary_slots(raw_output)  # type: ignore[arg-type]

    assert caught.value.code is SlotParseErrorCode.INVALID_ROOT


@pytest.mark.parametrize(
    "payload",
    [
        {"weather": "맑음"},
        {"people": "친구"},
        {"people": ("친구",)},
        {"event": ["놀이"]},
    ],
)
def test_rejects_schema_violation_without_rejected_value(
    payload: dict[str, object],
) -> None:
    with pytest.raises(SlotParseError) as caught:
        parse_diary_slots(payload)

    error = caught.value
    assert error.code is SlotParseErrorCode.SCHEMA_VALIDATION_FAILED
    assert error.issues
    assert str(payload) not in str(error)
    assert all(issue.location for issue in error.issues)
    assert all(issue.error_type for issue in error.issues)


def test_redacts_unknown_field_name_from_validation_issues() -> None:
    private_field_name = "아이 답변이 들어간 잘못된 필드"

    with pytest.raises(SlotParseError) as caught:
        parse_diary_slots({private_field_name: "민감한 값"})

    error = caught.value
    assert error.issues[0].location == ("<unknown>",)
    assert private_field_name not in repr(error.issues)
