"""Tests for the diary slot contract."""

import pytest
from pydantic import ValidationError

from app.dialogue import DiarySlots


SLOT_FIELDS = {
    "event",
    "people",
    "place",
    "emotion",
    "emotionReason",
    "memorableMoment",
}


def test_default_slots_are_unknown() -> None:
    slots = DiarySlots()

    assert slots.model_dump(mode="json", by_alias=True) == {
        "event": None,
        "people": [],
        "place": None,
        "emotion": None,
        "emotionReason": None,
        "memorableMoment": None,
    }


def test_validates_complete_camel_case_payload() -> None:
    payload = {
        "event": "친구와 블록 성을 만들었다",
        "people": ["친구"],
        "place": "유치원",
        "emotion": "신났다",
        "emotionReason": "성을 완성해서",
        "memorableMoment": "마지막 블록을 올린 순간",
    }

    slots = DiarySlots.model_validate(payload)

    assert slots.model_dump(mode="json", by_alias=True) == payload


def test_preserves_unknown_values_for_partial_input() -> None:
    slots = DiarySlots.model_validate({"event": "그림을 그렸다"})

    assert slots.event == "그림을 그렸다"
    assert slots.people == []
    assert slots.place is None
    assert slots.emotion is None
    assert slots.emotion_reason is None
    assert slots.memorable_moment is None


def test_accepts_python_field_names() -> None:
    slots = DiarySlots(
        emotion_reason="그림을 완성해서",
        memorable_moment="가족에게 그림을 보여 준 순간",
    )

    assert slots.model_dump(mode="json", by_alias=True) == {
        "event": None,
        "people": [],
        "place": None,
        "emotion": None,
        "emotionReason": "그림을 완성해서",
        "memorableMoment": "가족에게 그림을 보여 준 순간",
    }


def test_people_lists_are_not_shared_between_instances() -> None:
    first = DiarySlots()
    second = DiarySlots()

    first.people.append("친구")

    assert first.people == ["친구"]
    assert second.people == []


def test_rejects_unknown_field() -> None:
    with pytest.raises(ValidationError):
        DiarySlots.model_validate({"weather": "맑음"})


def test_rejects_invalid_assignment_after_validation() -> None:
    slots = DiarySlots(event="그림을 그렸다")

    with pytest.raises(ValidationError):
        slots.event = ["잘못된 타입"]  # type: ignore[assignment]

    assert slots.event == "그림을 그렸다"


@pytest.mark.parametrize(
    ("field", "invalid_value"),
    [
        ("people", "친구"),
        ("people", [1]),
        ("event", ["놀이"]),
        ("place", {"name": "유치원"}),
        ("emotion", ["신남"]),
        ("emotionReason", {"reason": "완성해서"}),
        ("memorableMoment", ["완성한 순간"]),
    ],
)
def test_rejects_invalid_slot_types(field: str, invalid_value: object) -> None:
    with pytest.raises(ValidationError):
        DiarySlots.model_validate({field: invalid_value})


def test_json_schema_matches_external_contract() -> None:
    schema = DiarySlots.model_json_schema(by_alias=True)
    properties = schema["properties"]

    assert set(properties) == SLOT_FIELDS
    assert schema["additionalProperties"] is False
    assert properties["people"]["type"] == "array"
    assert properties["people"]["items"]["type"] == "string"

    for field in SLOT_FIELDS - {"people"}:
        allowed_types = {item["type"] for item in properties[field]["anyOf"]}
        assert allowed_types == {"string", "null"}
