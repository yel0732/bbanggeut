"""Deterministic no-response recovery policy and trusted prompts."""

from dataclasses import dataclass
from enum import StrEnum
from types import MappingProxyType
from typing import Mapping

from app.dialogue.target_selector import (
    QuestionTarget,
    select_next_question_target,
)
from app.dialogue.termination_policy import (
    ChildTurnSignal,
    DialoguePolicyRequest,
    DialogueTerminationPolicy,
    DialogueTerminationReason,
    evaluate_dialogue_termination,
    next_consecutive_no_response_count,
)


class NoResponseRecoveryAction(StrEnum):
    """A deterministic next step after a finalized no-response turn."""

    NONE = "none"
    SIMPLIFY_QUESTION = "simplifyQuestion"
    ASK_TO_REPEAT = "askToRepeat"
    OFFER_CHOICES = "offerChoices"
    SKIP_CURRENT_TARGET = "skipCurrentTarget"


SIMPLIFIED_QUESTION_BY_TARGET: Mapping[QuestionTarget, str] = MappingProxyType(
    {
        QuestionTarget.EVENT: "오늘 있었던 일 하나를 말해 줄래?",
        QuestionTarget.EMOTION: "그때 마음이 어땠는지 말해 줄래?",
        QuestionTarget.EMOTION_REASON: "왜 그런 마음이 들었는지 말해 줄래?",
        QuestionTarget.MEMORABLE_MOMENT: "가장 기억나는 순간을 말해 줄래?",
        QuestionTarget.RESULT: "마지막에 어떻게 됐는지 말해 줄래?",
    }
)

CHOICE_QUESTION_BY_TARGET: Mapping[QuestionTarget, str] = MappingProxyType(
    {
        QuestionTarget.EVENT: "놀이나 다른 일 중에서 말하고 싶은 걸 골라 줄래?",
        QuestionTarget.EMOTION: "좋은 마음, 속상한 마음, 다른 마음 중에 골라 줄래?",
        QuestionTarget.EMOTION_REASON: "사람 때문인지, 일 때문인지, 다른 이유인지 골라 줄래?",
        QuestionTarget.MEMORABLE_MOMENT: "처음, 가운데, 마지막 중 기억나는 때를 골라 줄래?",
        QuestionTarget.RESULT: "끝났는지, 계속됐는지, 다른 일이 생겼는지 골라 줄래?",
    }
)

ASK_TO_REPEAT_PROMPT = "괜찮아, 천천히 다시 말해 줄래?"


@dataclass(frozen=True)
class NoResponseRecoveryPolicy:
    """Injectable thresholds for the ordered no-response recovery ladder."""

    simplify_at: int = 1
    ask_to_repeat_at: int = 2
    offer_choices_at: int = 3
    skip_at: int = 4
    terminate_at: int = 5

    def __post_init__(self) -> None:
        thresholds = (
            self.simplify_at,
            self.ask_to_repeat_at,
            self.offer_choices_at,
            self.skip_at,
            self.terminate_at,
        )
        if any(
            isinstance(value, bool)
            or not isinstance(value, int)
            or value < 1
            for value in thresholds
        ):
            raise ValueError("recovery thresholds must be positive integers")
        if any(
            current >= following
            for current, following in zip(thresholds, thresholds[1:])
        ):
            raise ValueError("recovery thresholds must be strictly increasing")


DEFAULT_NO_RESPONSE_RECOVERY_POLICY = NoResponseRecoveryPolicy()


@dataclass(frozen=True, repr=False)
class NoResponseRecoveryPrompt:
    """A trusted fixed prompt that contains no child-specific context."""

    action: NoResponseRecoveryAction
    target: QuestionTarget
    text: str

    def __post_init__(self) -> None:
        if not isinstance(self.action, NoResponseRecoveryAction):
            raise ValueError("action must be a NoResponseRecoveryAction")
        if self.action not in {
            NoResponseRecoveryAction.SIMPLIFY_QUESTION,
            NoResponseRecoveryAction.ASK_TO_REPEAT,
            NoResponseRecoveryAction.OFFER_CHOICES,
        }:
            raise ValueError("action does not produce a recovery prompt")
        if not isinstance(self.target, QuestionTarget):
            raise ValueError("target must be a QuestionTarget")
        if not isinstance(self.text, str) or not self.text.strip():
            raise ValueError("text must contain non-whitespace content")
        if self.text != _fixed_recovery_prompt_text(
            self.action,
            self.target,
        ):
            raise ValueError("text must match the registered recovery prompt")


@dataclass(frozen=True, repr=False)
class DialoguePolicyDecision:
    """Immutable result consumed by a future dialogue orchestration layer."""

    termination_reason: DialogueTerminationReason | None
    recovery_action: NoResponseRecoveryAction
    consecutive_no_responses: int
    skipped_targets: frozenset[QuestionTarget]
    next_target: QuestionTarget | None
    recovery_prompt: NoResponseRecoveryPrompt | None

    @property
    def should_terminate(self) -> bool:
        return self.termination_reason is not None

    def __post_init__(self) -> None:
        _validate_decision(self)


def evaluate_dialogue_policy(
    request: DialoguePolicyRequest,
    termination_policy: DialogueTerminationPolicy,
    recovery_policy: NoResponseRecoveryPolicy = (
        DEFAULT_NO_RESPONSE_RECOVERY_POLICY
    ),
) -> DialoguePolicyDecision:
    """Evaluate termination first, then choose one no-response recovery step."""

    no_response_count = next_consecutive_no_response_count(request)
    termination_reason = evaluate_dialogue_termination(
        request,
        termination_policy,
        no_response_termination_threshold=recovery_policy.terminate_at,
    )
    if termination_reason is not None:
        return _termination_decision(
            termination_reason,
            no_response_count,
            request.skipped_targets,
        )

    excluded_targets = frozenset(request.skipped_targets)
    if request.turn_signal is not ChildTurnSignal.NO_RESPONSE:
        next_target = select_next_question_target(
            request.slots,
            excluded_targets=excluded_targets,
        )
        if next_target is None:
            return _termination_decision(
                DialogueTerminationReason.INACTIVITY_TIMEOUT,
                no_response_count,
                excluded_targets,
            )
        return DialoguePolicyDecision(
            termination_reason=None,
            recovery_action=NoResponseRecoveryAction.NONE,
            consecutive_no_responses=no_response_count,
            skipped_targets=excluded_targets,
            next_target=next_target,
            recovery_prompt=None,
        )

    action = _select_recovery_action(no_response_count, recovery_policy)
    if action is NoResponseRecoveryAction.SKIP_CURRENT_TARGET:
        current_target = _require_current_target(request.current_target)
        skipped_targets = excluded_targets | {current_target}
        next_target = select_next_question_target(
            request.slots,
            excluded_targets=skipped_targets,
        )
        if next_target is None:
            return _termination_decision(
                DialogueTerminationReason.INACTIVITY_TIMEOUT,
                no_response_count,
                skipped_targets,
            )
        return DialoguePolicyDecision(
            termination_reason=None,
            recovery_action=action,
            consecutive_no_responses=no_response_count,
            skipped_targets=skipped_targets,
            next_target=next_target,
            recovery_prompt=None,
        )

    current_target = _require_current_target(request.current_target)
    prompt = (
        None
        if action is NoResponseRecoveryAction.NONE
        else build_no_response_recovery_prompt(action, current_target)
    )
    return DialoguePolicyDecision(
        termination_reason=None,
        recovery_action=action,
        consecutive_no_responses=no_response_count,
        skipped_targets=excluded_targets,
        next_target=current_target,
        recovery_prompt=prompt,
    )


def build_no_response_recovery_prompt(
    action: NoResponseRecoveryAction,
    target: QuestionTarget,
) -> NoResponseRecoveryPrompt:
    """Build a trusted fixed prompt for one supported recovery action."""

    text = _fixed_recovery_prompt_text(action, target)
    return NoResponseRecoveryPrompt(
        action=action,
        target=target,
        text=text,
    )


def _fixed_recovery_prompt_text(
    action: NoResponseRecoveryAction,
    target: QuestionTarget,
) -> str:
    if action is NoResponseRecoveryAction.SIMPLIFY_QUESTION:
        return SIMPLIFIED_QUESTION_BY_TARGET[target]
    elif action is NoResponseRecoveryAction.ASK_TO_REPEAT:
        return ASK_TO_REPEAT_PROMPT
    elif action is NoResponseRecoveryAction.OFFER_CHOICES:
        return CHOICE_QUESTION_BY_TARGET[target]
    raise ValueError("recovery action does not produce a prompt")


def _select_recovery_action(
    no_response_count: int,
    policy: NoResponseRecoveryPolicy,
) -> NoResponseRecoveryAction:
    if no_response_count >= policy.skip_at:
        return NoResponseRecoveryAction.SKIP_CURRENT_TARGET
    if no_response_count >= policy.offer_choices_at:
        return NoResponseRecoveryAction.OFFER_CHOICES
    if no_response_count >= policy.ask_to_repeat_at:
        return NoResponseRecoveryAction.ASK_TO_REPEAT
    if no_response_count >= policy.simplify_at:
        return NoResponseRecoveryAction.SIMPLIFY_QUESTION
    return NoResponseRecoveryAction.NONE


def _termination_decision(
    reason: DialogueTerminationReason,
    no_response_count: int,
    skipped_targets: frozenset[QuestionTarget],
) -> DialoguePolicyDecision:
    return DialoguePolicyDecision(
        termination_reason=reason,
        recovery_action=NoResponseRecoveryAction.NONE,
        consecutive_no_responses=no_response_count,
        skipped_targets=skipped_targets,
        next_target=None,
        recovery_prompt=None,
    )


def _require_current_target(
    target: QuestionTarget | None,
) -> QuestionTarget:
    if target is None:
        raise ValueError("currentTarget is required for recovery")
    return target


def _validate_decision(decision: DialoguePolicyDecision) -> None:
    if (
        decision.termination_reason is not None
        and not isinstance(
            decision.termination_reason,
            DialogueTerminationReason,
        )
    ):
        raise ValueError(
            "termination_reason must be a DialogueTerminationReason"
        )
    if not isinstance(
        decision.recovery_action,
        NoResponseRecoveryAction,
    ):
        raise ValueError("recovery_action must be a NoResponseRecoveryAction")
    if (
        isinstance(decision.consecutive_no_responses, bool)
        or not isinstance(decision.consecutive_no_responses, int)
        or decision.consecutive_no_responses < 0
    ):
        raise ValueError(
            "consecutive_no_responses must be a non-negative integer"
        )
    if not isinstance(decision.skipped_targets, frozenset) or any(
        not isinstance(target, QuestionTarget)
        for target in decision.skipped_targets
    ):
        raise ValueError("skipped_targets must contain QuestionTarget values")
    if decision.next_target is not None and not isinstance(
        decision.next_target,
        QuestionTarget,
    ):
        raise ValueError("next_target must be a QuestionTarget")
    if decision.recovery_prompt is not None and not isinstance(
        decision.recovery_prompt,
        NoResponseRecoveryPrompt,
    ):
        raise ValueError("recovery_prompt must be a NoResponseRecoveryPrompt")
    if decision.termination_reason is not None:
        if (
            decision.recovery_action is not NoResponseRecoveryAction.NONE
            or decision.next_target is not None
            or decision.recovery_prompt is not None
        ):
            raise ValueError("terminal decisions cannot contain recovery work")
        return
    if decision.recovery_action is NoResponseRecoveryAction.NONE:
        if decision.next_target is None or decision.recovery_prompt is not None:
            raise ValueError("NONE decisions require only a next target")
        return
    if decision.recovery_action is NoResponseRecoveryAction.SKIP_CURRENT_TARGET:
        if decision.next_target is None or decision.recovery_prompt is not None:
            raise ValueError("skip decisions require only a next target")
        if decision.next_target in decision.skipped_targets:
            raise ValueError("skip decisions cannot reuse a skipped target")
        return
    if decision.next_target is None or decision.recovery_prompt is None:
        raise ValueError("prompt recovery decisions require target and prompt")
    if (
        decision.recovery_prompt.action is not decision.recovery_action
        or decision.recovery_prompt.target is not decision.next_target
    ):
        raise ValueError("recovery prompt must match the decision")
