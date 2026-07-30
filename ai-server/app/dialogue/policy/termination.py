"""Define deterministic conversation termination policy."""

from dataclasses import dataclass
from enum import StrEnum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StrictInt,
    field_validator,
    model_validator,
)

from app.dialogue.schemas import DiarySlots
from app.dialogue.policy.target import (
    QUESTION_TARGET_SLOT_FIELDS,
    QuestionTarget,
    QuestionTargetSlotValues,
)


class ChildTurnSignal(StrEnum):
    """A finalized child-turn signal supplied by an upstream adapter."""

    ANSWERED = "answered"
    NO_RESPONSE = "noResponse"
    STOP_REQUESTED = "stopRequested"


class DialogueTerminationReason(StrEnum):
    """Provider-independent reasons why dialogue policy stops questioning."""

    CHILD_REQUESTED_STOP = "CHILD_REQUESTED_STOP"
    CHILD_REQUESTED_TOPIC_SWITCH = "CHILD_REQUESTED_TOPIC_SWITCH"
    INACTIVITY_TIMEOUT = "INACTIVITY_TIMEOUT"
    REQUIRED_SLOTS_FILLED = "REQUIRED_SLOTS_FILLED"
    MAX_QUESTION_REACHED = "MAX_QUESTION_REACHED"


class DialogueSlotSnapshot(BaseModel):
    """Immutable target-slot values used only by dialogue policy."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        strict=True,
        frozen=True,
    )

    event: str | None = Field(default=None, repr=False)
    emotion: str | None = Field(default=None, repr=False)
    emotion_reason: str | None = Field(
        default=None,
        alias="emotionReason",
        repr=False,
    )
    memorable_moment: str | None = Field(
        default=None,
        alias="memorableMoment",
        repr=False,
    )

    @classmethod
    def from_diary_slots(
        cls,
        slots: DiarySlots,
    ) -> "DialogueSlotSnapshot":
        return cls(
            event=slots.event,
            emotion=slots.emotion,
            emotion_reason=slots.emotion_reason,
            memorable_moment=slots.memorable_moment,
        )


class DialoguePolicyRequest(BaseModel):
    """Immutable-input snapshot used to evaluate one child turn."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        strict=True,
        frozen=True,
    )

    slots: DialogueSlotSnapshot = Field(repr=False)
    question_count: StrictInt = Field(alias="questionCount", ge=0)
    max_question_count: StrictInt = Field(alias="maxQuestionCount", gt=0)
    turn_signal: ChildTurnSignal = Field(alias="turnSignal")
    previous_consecutive_no_responses: StrictInt = Field(
        default=0,
        alias="previousConsecutiveNoResponses",
        ge=0,
    )
    current_target: QuestionTarget | None = Field(
        default=None,
        alias="currentTarget",
    )
    skipped_targets: frozenset[QuestionTarget] = Field(
        default_factory=frozenset,
        alias="skippedTargets",
    )

    @field_validator("turn_signal", mode="before")
    @classmethod
    def parse_turn_signal(cls, value: object) -> object:
        """Accept the documented JSON enum value under strict validation."""

        if isinstance(value, str):
            return ChildTurnSignal(value)
        return value

    @field_validator("current_target", mode="before")
    @classmethod
    def parse_current_target(cls, value: object) -> object:
        """Accept the documented JSON target value under strict validation."""

        if isinstance(value, str):
            return QuestionTarget(value)
        return value

    @field_validator("skipped_targets", mode="before")
    @classmethod
    def parse_skipped_targets(cls, value: object) -> object:
        """Normalize a JSON array to a strict immutable target collection."""

        if isinstance(value, (list, tuple, set, frozenset)):
            return frozenset(
                QuestionTarget(target)
                if isinstance(target, str)
                else target
                for target in value
            )
        return value

    @field_validator("slots", mode="before")
    @classmethod
    def copy_slots_snapshot(cls, value: object) -> object:
        """Detach policy input from mutable session slot state."""

        if isinstance(value, DialogueSlotSnapshot):
            return value
        if isinstance(value, DiarySlots):
            return DialogueSlotSnapshot.from_diary_slots(value)
        if isinstance(value, dict):
            return DialogueSlotSnapshot.from_diary_slots(
                DiarySlots.model_validate(value)
            )
        return value

    @model_validator(mode="after")
    def validate_policy_context(self) -> "DialoguePolicyRequest":
        if self.question_count > self.max_question_count:
            raise ValueError("questionCount cannot exceed maxQuestionCount")
        if (
            self.turn_signal is ChildTurnSignal.NO_RESPONSE
            and self.current_target is None
        ):
            raise ValueError("currentTarget is required for a no-response turn")
        if (
            self.turn_signal is ChildTurnSignal.NO_RESPONSE
            and self.current_target in self.skipped_targets
        ):
            raise ValueError("currentTarget cannot already be skipped")
        return self


@dataclass(frozen=True)
class DialogueTerminationPolicy:
    """Required targets explicitly chosen by product policy."""

    required_targets: frozenset[QuestionTarget]

    def __post_init__(self) -> None:
        _validate_required_targets(self.required_targets)


def next_consecutive_no_response_count(
    request: DialoguePolicyRequest,
) -> int:
    """Return the no-response count after applying the current turn signal."""

    if request.turn_signal is ChildTurnSignal.NO_RESPONSE:
        return request.previous_consecutive_no_responses + 1
    return 0


def evaluate_dialogue_termination(
    request: DialoguePolicyRequest,
    policy: DialogueTerminationPolicy,
    *,
    no_response_termination_threshold: int,
) -> DialogueTerminationReason | None:
    """Evaluate terminal conditions in a stable, cause-preserving order."""

    _validate_no_response_termination_threshold(
        no_response_termination_threshold
    )
    consecutive_no_responses = next_consecutive_no_response_count(request)

    if request.turn_signal is ChildTurnSignal.STOP_REQUESTED:
        return DialogueTerminationReason.CHILD_REQUESTED_STOP
    if consecutive_no_responses >= no_response_termination_threshold:
        return DialogueTerminationReason.INACTIVITY_TIMEOUT
    if are_required_targets_filled(request.slots, policy.required_targets):
        return DialogueTerminationReason.REQUIRED_SLOTS_FILLED
    if request.question_count >= request.max_question_count:
        return DialogueTerminationReason.MAX_QUESTION_REACHED
    return None


def are_required_targets_filled(
    slots: QuestionTargetSlotValues,
    required_targets: frozenset[QuestionTarget],
) -> bool:
    """Return whether every explicitly required target has confirmed content."""

    _validate_required_targets(required_targets)
    return all(
        _is_filled(getattr(slots, QUESTION_TARGET_SLOT_FIELDS[target]))
        for target in required_targets
    )


def _validate_required_targets(
    required_targets: frozenset[QuestionTarget],
) -> None:
    if not isinstance(required_targets, frozenset):
        raise ValueError("required_targets must be a frozenset")
    if not required_targets:
        raise ValueError("required_targets must not be empty")
    if any(
        not isinstance(target, QuestionTarget)
        for target in required_targets
    ):
        raise ValueError("required_targets must contain QuestionTarget values")


def _validate_no_response_termination_threshold(threshold: int) -> None:
    if (
        isinstance(threshold, bool)
        or not isinstance(threshold, int)
        or threshold < 1
    ):
        raise ValueError(
            "no_response_termination_threshold must be a positive integer"
        )


def _is_filled(value: str | None) -> bool:
    return value is not None and bool(value.strip())
