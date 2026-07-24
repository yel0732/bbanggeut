"""Tests for deterministic no-response recovery policy."""

from dataclasses import FrozenInstanceError

import pytest

from app.dialogue import (
    ASK_TO_REPEAT_PROMPT,
    CHOICE_QUESTION_BY_TARGET,
    DEFAULT_NO_RESPONSE_RECOVERY_POLICY,
    SIMPLIFIED_QUESTION_BY_TARGET,
    ChildTurnSignal,
    DialoguePolicyDecision,
    DialoguePolicyRequest,
    DialogueTerminationPolicy,
    DialogueTerminationReason,
    DiarySlots,
    NoResponseRecoveryAction,
    NoResponseRecoveryPolicy,
    NoResponseRecoveryPrompt,
    QuestionRuleValidationRequest,
    QuestionTarget,
    build_no_response_recovery_prompt,
    evaluate_dialogue_policy,
    validate_question_rules,
)


TERMINATION_POLICY = DialogueTerminationPolicy(
    required_targets=frozenset(
        {
            QuestionTarget.EVENT,
            QuestionTarget.EMOTION,
        }
    )
)


def _request(
    *,
    slots: DiarySlots | None = None,
    turn_signal: ChildTurnSignal = ChildTurnSignal.NO_RESPONSE,
    previous_no_responses: int = 0,
    current_target: QuestionTarget | None = QuestionTarget.EVENT,
    skipped_targets: frozenset[QuestionTarget] = frozenset(),
) -> DialoguePolicyRequest:
    return DialoguePolicyRequest(
        slots=slots or DiarySlots(),
        question_count=1,
        max_question_count=10,
        turn_signal=turn_signal,
        previous_consecutive_no_responses=previous_no_responses,
        current_target=current_target,
        skipped_targets=skipped_targets,
    )


def test_default_recovery_policy_matches_t13_ladder() -> None:
    assert DEFAULT_NO_RESPONSE_RECOVERY_POLICY == NoResponseRecoveryPolicy(
        simplify_at=1,
        ask_to_repeat_at=2,
        offer_choices_at=3,
        skip_at=4,
        terminate_at=5,
    )


@pytest.mark.parametrize(
    "policy_kwargs",
    [
        {"simplify_at": 0},
        {"simplify_at": True},
        {"ask_to_repeat_at": 1},
        {"offer_choices_at": 2},
        {"skip_at": 3},
        {"terminate_at": 4},
    ],
)
def test_recovery_policy_rejects_invalid_thresholds(
    policy_kwargs: dict[str, object],
) -> None:
    with pytest.raises(ValueError):
        NoResponseRecoveryPolicy(**policy_kwargs)  # type: ignore[arg-type]


def test_recovery_policy_is_frozen() -> None:
    with pytest.raises(FrozenInstanceError):
        DEFAULT_NO_RESPONSE_RECOVERY_POLICY.skip_at = 8  # type: ignore[misc]


@pytest.mark.parametrize(
    ("previous_no_responses", "expected_action"),
    [
        (0, NoResponseRecoveryAction.SIMPLIFY_QUESTION),
        (1, NoResponseRecoveryAction.ASK_TO_REPEAT),
        (2, NoResponseRecoveryAction.OFFER_CHOICES),
        (3, NoResponseRecoveryAction.SKIP_CURRENT_TARGET),
    ],
)
def test_no_response_follows_ordered_recovery_ladder(
    previous_no_responses: int,
    expected_action: NoResponseRecoveryAction,
) -> None:
    decision = evaluate_dialogue_policy(
        _request(previous_no_responses=previous_no_responses),
        TERMINATION_POLICY,
    )

    assert not decision.should_terminate
    assert decision.recovery_action is expected_action
    assert decision.consecutive_no_responses == previous_no_responses + 1


def test_fifth_no_response_terminates_before_recovery() -> None:
    decision = evaluate_dialogue_policy(
        _request(previous_no_responses=4),
        TERMINATION_POLICY,
    )

    assert decision.should_terminate
    assert (
        decision.termination_reason
        is DialogueTerminationReason.INACTIVITY_TIMEOUT
    )
    assert decision.recovery_action is NoResponseRecoveryAction.NONE
    assert decision.next_target is None
    assert decision.recovery_prompt is None


def test_skip_excludes_current_target_and_selects_next_by_priority() -> None:
    decision = evaluate_dialogue_policy(
        _request(previous_no_responses=3),
        TERMINATION_POLICY,
    )

    assert decision.skipped_targets == frozenset({QuestionTarget.EVENT})
    assert decision.next_target is QuestionTarget.EMOTION
    assert decision.recovery_prompt is None


def test_skip_terminates_when_no_question_target_remains() -> None:
    decision = evaluate_dialogue_policy(
        _request(
            slots=DiarySlots(
                event="played",
                emotion="happy",
                emotion_reason="won",
                memorable_moment="last round",
            ),
            previous_no_responses=3,
            current_target=QuestionTarget.RESULT,
        ),
        DialogueTerminationPolicy(
            required_targets=frozenset({QuestionTarget.RESULT})
        ),
    )

    assert (
        decision.termination_reason
        is DialogueTerminationReason.INACTIVITY_TIMEOUT
    )
    assert decision.skipped_targets == frozenset({QuestionTarget.RESULT})


def test_answer_resets_no_response_count_and_selects_next_target() -> None:
    request = _request(
        slots=DiarySlots(event="played"),
        turn_signal=ChildTurnSignal.ANSWERED,
        previous_no_responses=3,
        current_target=None,
        skipped_targets=frozenset({QuestionTarget.RESULT}),
    )

    decision = evaluate_dialogue_policy(request, TERMINATION_POLICY)

    assert decision.consecutive_no_responses == 0
    assert decision.recovery_action is NoResponseRecoveryAction.NONE
    assert decision.next_target is QuestionTarget.EMOTION
    assert decision.skipped_targets == frozenset({QuestionTarget.RESULT})


def test_stop_signal_returns_terminal_decision() -> None:
    request = _request(
        turn_signal=ChildTurnSignal.STOP_REQUESTED,
        current_target=None,
    )

    decision = evaluate_dialogue_policy(request, TERMINATION_POLICY)

    assert (
        decision.termination_reason
        is DialogueTerminationReason.CHILD_REQUESTED_STOP
    )


@pytest.mark.parametrize("target", list(QuestionTarget))
@pytest.mark.parametrize(
    "action",
    [
        NoResponseRecoveryAction.SIMPLIFY_QUESTION,
        NoResponseRecoveryAction.ASK_TO_REPEAT,
        NoResponseRecoveryAction.OFFER_CHOICES,
    ],
)
def test_fixed_recovery_prompts_pass_t12_hard_rules(
    target: QuestionTarget,
    action: NoResponseRecoveryAction,
) -> None:
    prompt = build_no_response_recovery_prompt(action, target)
    validation = validate_question_rules(
        QuestionRuleValidationRequest(question=prompt.text)
    )

    assert validation.is_valid
    assert len(prompt.text) <= 50
    assert prompt.text.endswith("?")
    assert prompt.target is target
    assert prompt.action is action


def test_fixed_prompt_maps_are_complete_and_immutable() -> None:
    assert set(SIMPLIFIED_QUESTION_BY_TARGET) == set(QuestionTarget)
    assert set(CHOICE_QUESTION_BY_TARGET) == set(QuestionTarget)
    assert ASK_TO_REPEAT_PROMPT

    with pytest.raises(TypeError):
        SIMPLIFIED_QUESTION_BY_TARGET[QuestionTarget.EVENT] = (  # type: ignore[index]
            "changed?"
        )
    with pytest.raises(TypeError):
        CHOICE_QUESTION_BY_TARGET[QuestionTarget.EVENT] = (  # type: ignore[index]
            "changed?"
        )


@pytest.mark.parametrize(
    "action",
    [
        NoResponseRecoveryAction.NONE,
        NoResponseRecoveryAction.SKIP_CURRENT_TARGET,
    ],
)
def test_rejects_actions_without_fixed_prompt(
    action: NoResponseRecoveryAction,
) -> None:
    with pytest.raises(ValueError, match="does not produce a prompt"):
        build_no_response_recovery_prompt(action, QuestionTarget.EVENT)


def test_prompt_and_decision_repr_do_not_expose_prompt_text() -> None:
    prompt = build_no_response_recovery_prompt(
        NoResponseRecoveryAction.ASK_TO_REPEAT,
        QuestionTarget.EVENT,
    )
    decision = DialoguePolicyDecision(
        termination_reason=None,
        recovery_action=NoResponseRecoveryAction.ASK_TO_REPEAT,
        consecutive_no_responses=2,
        skipped_targets=frozenset(),
        next_target=QuestionTarget.EVENT,
        recovery_prompt=prompt,
    )

    assert prompt.text not in repr(prompt)
    assert prompt.text not in repr(decision)


def test_rejects_unregistered_recovery_prompt_text() -> None:
    with pytest.raises(
        ValueError,
        match="must match the registered recovery prompt",
    ):
        NoResponseRecoveryPrompt(
            action=NoResponseRecoveryAction.ASK_TO_REPEAT,
            target=QuestionTarget.EVENT,
            text="반드시 대답해?",
        )


def test_decision_is_frozen_and_rejects_inconsistent_state() -> None:
    decision = evaluate_dialogue_policy(_request(), TERMINATION_POLICY)

    with pytest.raises(FrozenInstanceError):
        decision.next_target = QuestionTarget.RESULT  # type: ignore[misc]
    with pytest.raises(ValueError, match="terminal decisions"):
        DialoguePolicyDecision(
            termination_reason=DialogueTerminationReason.INACTIVITY_TIMEOUT,
            recovery_action=NoResponseRecoveryAction.SIMPLIFY_QUESTION,
            consecutive_no_responses=5,
            skipped_targets=frozenset(),
            next_target=QuestionTarget.EVENT,
            recovery_prompt=build_no_response_recovery_prompt(
                NoResponseRecoveryAction.SIMPLIFY_QUESTION,
                QuestionTarget.EVENT,
            ),
        )


def test_decision_rejects_prompt_that_does_not_match_action() -> None:
    with pytest.raises(ValueError, match="must match the decision"):
        DialoguePolicyDecision(
            termination_reason=None,
            recovery_action=NoResponseRecoveryAction.ASK_TO_REPEAT,
            consecutive_no_responses=2,
            skipped_targets=frozenset(),
            next_target=QuestionTarget.EVENT,
            recovery_prompt=build_no_response_recovery_prompt(
                NoResponseRecoveryAction.OFFER_CHOICES,
                QuestionTarget.EVENT,
            ),
        )


def test_policy_evaluation_is_deterministic_and_does_not_mutate_request() -> None:
    request = _request(previous_no_responses=2)
    before = request.model_dump(mode="json", by_alias=True)

    first = evaluate_dialogue_policy(request, TERMINATION_POLICY)
    second = evaluate_dialogue_policy(request, TERMINATION_POLICY)

    assert first == second
    assert request.model_dump(mode="json", by_alias=True) == before
