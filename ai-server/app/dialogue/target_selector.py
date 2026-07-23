"""Deterministic policy for selecting the next diary question target."""

from enum import StrEnum

from app.dialogue.schemas import DiarySlots


class QuestionTarget(StrEnum):
    """A single diary slot that the next question should address."""

    EVENT = "event"
    EMOTION = "emotion"
    EMOTION_REASON = "emotionReason"
    MEMORABLE_MOMENT = "memorableMoment"
    RESULT = "result"


_TARGET_RULES: tuple[tuple[QuestionTarget, str], ...] = (
    (QuestionTarget.EVENT, "event"),
    (QuestionTarget.EMOTION, "emotion"),
    (QuestionTarget.EMOTION_REASON, "emotion_reason"),
    (QuestionTarget.MEMORABLE_MOMENT, "memorable_moment"),
    (QuestionTarget.RESULT, "result"),
)

# This is only the order for choosing a question target. Required slots and
# conversation termination belong to the separate termination policy.
QUESTION_TARGET_PRIORITY: tuple[QuestionTarget, ...] = tuple(
    target for target, _ in _TARGET_RULES
)


def select_next_question_target(
    slots: DiarySlots,
) -> QuestionTarget | None:
    """Return the first unfilled target without changing the slots.

    ``None`` means that no target remains in this questioning order. It does
    not by itself mean that the conversation should terminate.
    """

    for target, field_name in _TARGET_RULES:
        if not _is_filled(getattr(slots, field_name)):
            return target
    return None


def _is_filled(value: str | None) -> bool:
    return value is not None and bool(value.strip())
