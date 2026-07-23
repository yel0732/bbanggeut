"""Validated merge policy for diary slots."""

from app.dialogue.schemas import DiarySlots


def merge_diary_slots(
    current_slots: DiarySlots,
    extracted_slots: DiarySlots,
) -> DiarySlots:
    """Return new slots containing only explicitly confirmed updates."""

    current = _revalidate(current_slots)
    extracted = _revalidate(extracted_slots)

    merged = {
        "event": _merge_scalar(current.event, extracted.event),
        "people": _merge_people(current.people, extracted.people),
        "place": _merge_scalar(current.place, extracted.place),
        "emotion": _merge_scalar(current.emotion, extracted.emotion),
        "emotionReason": _merge_scalar(
            current.emotion_reason,
            extracted.emotion_reason,
        ),
        "memorableMoment": _merge_scalar(
            current.memorable_moment,
            extracted.memorable_moment,
        ),
        "result": _merge_scalar(current.result, extracted.result),
    }
    return DiarySlots.model_validate(merged)


def _revalidate(slots: DiarySlots) -> DiarySlots:
    payload = {
        field_name: getattr(slots, field_name)
        for field_name in DiarySlots.model_fields
    }
    return DiarySlots.model_validate(payload)


def _merge_scalar(current: str | None, extracted: str | None) -> str | None:
    if extracted is None:
        return current

    normalized = extracted.strip()
    return normalized or current


def _merge_people(current: list[str], extracted: list[str]) -> list[str]:
    merged: list[str] = []
    seen: set[str] = set()

    for person in [*current, *extracted]:
        normalized = person.strip()
        if normalized and normalized not in seen:
            merged.append(normalized)
            seen.add(normalized)

    return merged
