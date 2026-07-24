"""Tests for deterministic dialogue termination policy."""

from dataclasses import FrozenInstanceError

import pytest
from pydantic import ValidationError

from app.dialogue import (
    QUESTION_TARGET_SLOT_FIELDS,
    ChildTurnSignal,
    DialoguePolicyRequest,
    DialogueSlotSnapshot,
    DialogueTerminationPolicy,
    DialogueTerminationReason,
    DiarySlots,
    QuestionTarget,
    are_required_targets_filled,
    evaluate_dialogue_termination,
    next_consecutive_no_response_count,
)


REQUIRED_TARGETS = frozenset(
    {
        QuestionTarget.EVENT,
        QuestionTarget.EMOTION,
    }
)
TERMINATION_POLICY = DialogueTerminationPolicy(
    required_targets=REQUIRED_TARGETS,
)


def _request(
    *,
    slots: DiarySlots | None = None,
    question_count: int = 1,
    max_question_count: int = 5,
    turn_signal: ChildTurnSignal = ChildTurnSignal.ANSWERED,
    previous_no_responses: int = 0,
    current_target: QuestionTarget | None = None,
) -> DialoguePolicyRequest:
    return DialoguePolicyRequest(
        slots=slots or DiarySlots(),
        question_count=question_count,
        max_question_count=max_question_count,
        turn_signal=turn_signal,
        previous_consecutive_no_responses=previous_no_responses,
        current_target=current_target,
    )


def _evaluate(
    request: DialoguePolicyRequest,
    *,
    threshold: int = 5,
) -> DialogueTerminationReason | None:
    return evaluate_dialogue_termination(
        request,
        TERMINATION_POLICY,
        no_response_termination_threshold=threshold,
    )


def test_request_accepts_external_camel_case_contract() -> None:
    request = DialoguePolicyRequest.model_validate(
        {
            "slots": {"event": "played blocks"},
            "questionCount": 2,
            "maxQuestionCount": 5,
            "turnSignal": "noResponse",
            "previousConsecutiveNoResponses": 1,
            "currentTarget": "emotion",
            "skippedTargets": ["result"],
        }
    )

    assert request.question_count == 2
    assert request.turn_signal is ChildTurnSignal.NO_RESPONSE
    assert request.current_target is QuestionTarget.EMOTION
    assert request.skipped_targets == frozenset({QuestionTarget.RESULT})
    assert request.slots.model_dump(mode="json", by_alias=True) == {
        "event": "played blocks",
        "emotion": None,
        "emotionReason": None,
        "memorableMoment": None,
        "result": None,
    }


@pytest.mark.parametrize(
    "payload_update",
    [
        {"questionCount": "1"},
        {"questionCount": True},
        {"maxQuestionCount": 0},
        {"previousConsecutiveNoResponses": -1},
        {"questionCount": 6},
        {"unknown": "field"},
    ],
)
def test_request_rejects_invalid_policy_context(
    payload_update: dict[str, object],
) -> None:
    payload: dict[str, object] = {
        "slots": {},
        "questionCount": 1,
        "maxQuestionCount": 5,
        "turnSignal": "answered",
    }
    payload.update(payload_update)

    with pytest.raises(ValidationError):
        DialoguePolicyRequest.model_validate(payload)


def test_no_response_requires_current_target() -> None:
    with pytest.raises(ValidationError, match="currentTarget is required"):
        _request(turn_signal=ChildTurnSignal.NO_RESPONSE)


def test_no_response_rejects_already_skipped_current_target() -> None:
    with pytest.raises(ValidationError, match="cannot already be skipped"):
        DialoguePolicyRequest(
            slots=DiarySlots(),
            question_count=1,
            max_question_count=5,
            turn_signal=ChildTurnSignal.NO_RESPONSE,
            current_target=QuestionTarget.EVENT,
            skipped_targets=frozenset({QuestionTarget.EVENT}),
        )


def test_request_detaches_slots_and_hides_them_from_repr() -> None:
    slots = DiarySlots(event="PRIVATE_EVENT")
    request = _request(slots=slots)

    slots.event = "changed outside"

    assert request.slots.event == "PRIVATE_EVENT"
    assert "PRIVATE_EVENT" not in repr(request)


def test_request_is_an_immutable_snapshot() -> None:
    request = _request(slots=DiarySlots(event="PRIVATE_EVENT"))

    with pytest.raises(ValidationError, match="frozen"):
        request.question_count = 2
    with pytest.raises(ValidationError, match="frozen"):
        request.slots.event = "changed inside"

    assert request.slots.event == "PRIVATE_EVENT"
    assert "PRIVATE_EVENT" not in repr(request.slots)


def test_slot_snapshot_rejects_non_string_values() -> None:
    with pytest.raises(ValidationError):
        DialogueSlotSnapshot(event=1)  # type: ignore[arg-type]


def test_termination_policy_is_explicit_strict_and_frozen() -> None:
    with pytest.raises(ValueError, match="must not be empty"):
        DialogueTerminationPolicy(required_targets=frozenset())
    with pytest.raises(ValueError, match="must be a frozenset"):
        DialogueTerminationPolicy(  # type: ignore[arg-type]
            required_targets={QuestionTarget.EVENT},
        )
    with pytest.raises(ValueError, match="QuestionTarget values"):
        DialogueTerminationPolicy(  # type: ignore[arg-type]
            required_targets=frozenset({"event"}),
        )
    with pytest.raises(FrozenInstanceError):
        TERMINATION_POLICY.required_targets = frozenset(  # type: ignore[misc]
            {QuestionTarget.RESULT}
        )


def test_target_slot_mapping_is_complete_and_immutable() -> None:
    assert set(QUESTION_TARGET_SLOT_FIELDS) == set(QuestionTarget)

    with pytest.raises(TypeError):
        QUESTION_TARGET_SLOT_FIELDS[QuestionTarget.EVENT] = (  # type: ignore[index]
            "result"
        )


@pytest.mark.parametrize("empty_value", [None, "", "   "])
def test_required_targets_treat_empty_text_as_unfilled(
    empty_value: str | None,
) -> None:
    slots = DiarySlots(event="played blocks", emotion=empty_value)

    assert not are_required_targets_filled(slots, REQUIRED_TARGETS)


def test_required_targets_are_driven_by_injected_policy() -> None:
    slots = DiarySlots(event="played blocks", emotion="happy")

    assert are_required_targets_filled(slots, REQUIRED_TARGETS)
    assert not are_required_targets_filled(
        slots,
        frozenset({QuestionTarget.RESULT}),
    )


@pytest.mark.parametrize(
    ("required_targets", "message"),
    [
        (frozenset(), "must not be empty"),
        ({QuestionTarget.EVENT}, "must be a frozenset"),
        (frozenset({"event"}), "QuestionTarget values"),
    ],
)
def test_required_target_helper_rejects_invalid_policy_input(
    required_targets: object,
    message: str,
) -> None:
    with pytest.raises(ValueError, match=message):
        are_required_targets_filled(
            DialogueSlotSnapshot(),
            required_targets,  # type: ignore[arg-type]
        )


def test_no_response_count_increments_and_answer_resets() -> None:
    no_response = _request(
        turn_signal=ChildTurnSignal.NO_RESPONSE,
        previous_no_responses=2,
        current_target=QuestionTarget.EVENT,
    )
    answered = _request(previous_no_responses=2)

    assert next_consecutive_no_response_count(no_response) == 3
    assert next_consecutive_no_response_count(answered) == 0


def test_stop_request_has_highest_termination_priority() -> None:
    request = _request(
        slots=DiarySlots(event="played", emotion="happy"),
        question_count=5,
        turn_signal=ChildTurnSignal.STOP_REQUESTED,
        previous_no_responses=4,
    )

    assert _evaluate(request) is DialogueTerminationReason.CHILD_REQUESTED_STOP


def test_inactivity_has_priority_over_slot_and_question_limits() -> None:
    request = _request(
        slots=DiarySlots(event="played", emotion="happy"),
        question_count=5,
        turn_signal=ChildTurnSignal.NO_RESPONSE,
        previous_no_responses=4,
        current_target=QuestionTarget.RESULT,
    )

    assert _evaluate(request) is DialogueTerminationReason.INACTIVITY_TIMEOUT


def test_answered_turn_resets_previous_no_responses_before_termination() -> None:
    request = _request(
        previous_no_responses=5,
        turn_signal=ChildTurnSignal.ANSWERED,
    )

    assert _evaluate(request) is None


def test_required_slots_have_priority_over_question_limit() -> None:
    request = _request(
        slots=DiarySlots(event="played", emotion="happy"),
        question_count=5,
    )

    assert _evaluate(
        request,
    ) is DialogueTerminationReason.REQUIRED_SLOTS_FILLED


def test_question_limit_stops_when_required_slots_are_missing() -> None:
    request = _request(question_count=5)

    assert _evaluate(
        request,
    ) is DialogueTerminationReason.MAX_QUESTION_REACHED


def test_non_terminal_request_returns_none_without_mutation() -> None:
    request = _request(slots=DiarySlots(event="played"))
    before = request.model_dump(mode="json", by_alias=True)

    first = _evaluate(request)
    second = _evaluate(request)

    assert first is None
    assert second is first
    assert request.model_dump(mode="json", by_alias=True) == before


@pytest.mark.parametrize(
    "threshold",
    [
        -1,
        0,
        True,
    ],
)
def test_rejects_invalid_no_response_termination_threshold(
    threshold: int,
) -> None:
    with pytest.raises(ValueError):
        _evaluate(
            _request(),
            threshold=threshold,
        )
