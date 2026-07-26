"""Enrich explicitly named diary participants deterministically."""

from app.dialogue.schemas import DiarySlots


_PERSON_ROLES: tuple[str, ...] = (
    "선생님",
    "친구",
    "엄마",
    "아빠",
    "어머니",
    "아버지",
    "동생",
    "형",
    "누나",
    "언니",
    "오빠",
    "할머니",
    "할아버지",
)


def enrich_explicit_people(
    slots: DiarySlots,
    child_answer: str,
) -> DiarySlots:
    """Add explicitly mentioned people without replacing existing values."""

    people = list(slots.people)
    seen = set(people)
    for role in _PERSON_ROLES:
        if role in child_answer and role not in seen:
            people.append(role)
            seen.add(role)
    return slots.model_copy(update={"people": people}, deep=True)
