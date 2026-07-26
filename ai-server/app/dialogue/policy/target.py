"""Select the next diary question target deterministically."""

from collections.abc import Collection
from enum import StrEnum
from types import MappingProxyType
from typing import Mapping, Protocol


class QuestionTargetSlotValues(Protocol):
    """Structural slot values required by question-target policies."""

    event: str | None
    emotion: str | None
    emotion_reason: str | None
    memorable_moment: str | None


class QuestionTarget(StrEnum):
    """A single diary slot that the next question should address."""

    EVENT = "event"
    EMOTION = "emotion"
    EMOTION_REASON = "emotionReason"
    MEMORABLE_MOMENT = "memorableMoment"


QUESTION_TARGET_SLOT_FIELDS: Mapping[QuestionTarget, str] = MappingProxyType(
    {
        QuestionTarget.EVENT: "event",
        QuestionTarget.EMOTION: "emotion",
        QuestionTarget.EMOTION_REASON: "emotion_reason",
        QuestionTarget.MEMORABLE_MOMENT: "memorable_moment",
    }
)

# This is only the order for choosing a question target. Required slots and
# conversation termination belong to the separate termination policy.
QUESTION_TARGET_PRIORITY: tuple[QuestionTarget, ...] = tuple(
    QUESTION_TARGET_SLOT_FIELDS
)


def select_next_question_target(
    slots: QuestionTargetSlotValues,
    *,
    excluded_targets: Collection[QuestionTarget] = (),
) -> QuestionTarget | None:
    """Return the first unfilled target without changing the slots.

    ``None`` means that no target remains in this questioning order. It does
    not by itself mean that the conversation should terminate.
    """

    excluded = frozenset(excluded_targets)
    if any(not isinstance(target, QuestionTarget) for target in excluded):
        raise ValueError("excluded_targets must contain QuestionTarget values")

    for target, field_name in QUESTION_TARGET_SLOT_FIELDS.items():
        if target in excluded:
            continue
        if not _is_filled(getattr(slots, field_name)):
            return target
    return None


def _is_filled(value: str | None) -> bool:
    return value is not None and bool(value.strip())
