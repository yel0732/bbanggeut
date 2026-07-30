"""Adapt GMS completions to dialogue-domain contracts."""

from typing import Protocol

from app.dialogue.policy.target import QuestionTarget
from app.dialogue.question.judge import (
    QuestionJudgeParseError,
    SemanticQuestionValidationRequest,
    build_semantic_question_validation_prompt,
    parse_semantic_question_decision,
)
from app.dialogue.question.prompts import (
    DiaryQuestionGenerationRequest,
    QuestionGenerationPrompt,
)
from app.dialogue.question.provider import QuestionProviderError
from app.dialogue.question.provider import QuestionProviderErrorCode
from app.dialogue.question.validator import (
    QuestionRuleValidationRequest,
    QuestionRuleValidationPolicy,
    QuestionValidationCode,
    QuestionValidationIssue,
    QuestionValidationResult,
    combine_question_validation_results,
    validate_question_rules,
    validate_target_assertion_grounding,
)
from app.providers.gms_chat import GmsChatMessage


class GmsCompletionClient(Protocol):
    """Minimum GMS capability needed by dialogue adapters."""

    async def complete(
        self,
        messages: tuple[GmsChatMessage, ...],
    ) -> str:
        """Return one validated text completion."""


class GmsQuestionGenerationProvider:
    """Translate T11 prompts to GMS Chat Completions messages."""

    def __init__(self, client: GmsCompletionClient) -> None:
        self._client = client

    async def generate_question(
        self,
        prompt: QuestionGenerationPrompt,
    ) -> str:
        return await self._client.complete(
            (
                GmsChatMessage(role="developer", content=prompt.system),
                GmsChatMessage(role="user", content=prompt.user),
            )
        )


class GmsGeneratedQuestionValidator:
    """Run T12 hard rules before asking GMS for semantic validation."""

    def __init__(self, client: GmsCompletionClient) -> None:
        self._client = client

    async def validate_question(
        self,
        request: DiaryQuestionGenerationRequest,
        question: str,
    ) -> QuestionValidationResult:
        rule_result = validate_question_rules(
            QuestionRuleValidationRequest(
                question=question,
                recent_questions=request.recent_questions,
            ),
            QuestionRuleValidationPolicy(
                max_characters=90,
                allow_leading_statement=True,
            ),
        )
        if not rule_result.is_valid:
            return combine_question_validation_results(rule_result, None)
        grounding_result = validate_target_assertion_grounding(
            target=request.target,
            question=rule_result.normalized_question,
            current_slots=request.current_slots,
            child_answer=request.child_answer,
        )
        if not grounding_result.is_valid:
            return grounding_result
        if (
            request.redirect_context is not None
            and request.redirect_context.child_question
            and (
                not rule_result.normalized_question.startswith("나는")
                or not _has_child_question_bridge(
                    rule_result.normalized_question
                )
            )
        ):
            return QuestionValidationResult(
                normalized_question=rule_result.normalized_question,
                issues=(
                    QuestionValidationIssue(
                        QuestionValidationCode.TARGET_MISMATCH
                    ),
                ),
            )
        if (
            request.selective_semantic_validation
            and request.redirect_context is None
            and _question_matches_target(
                _extract_final_question(rule_result.normalized_question),
                request.target,
            )
        ):
            return QuestionValidationResult(
                normalized_question=rule_result.normalized_question,
                issues=(),
            )

        semantic_prompt = build_semantic_question_validation_prompt(
            SemanticQuestionValidationRequest(
                target=request.target,
                question=rule_result.normalized_question,
                child_answer=request.child_answer,
                current_question=request.current_question,
                current_slots=request.current_slots,
                topic_history=request.topic_history,
                recent_questions=request.recent_questions,
                redirect_context=request.redirect_context,
            )
        )
        raw_decision = await self._client.complete(
            (
                GmsChatMessage(
                    role="developer",
                    content=semantic_prompt.system,
                ),
                GmsChatMessage(role="user", content=semantic_prompt.user),
            )
        )
        try:
            decision = parse_semantic_question_decision(raw_decision)
        except QuestionJudgeParseError:
            raise QuestionProviderError(
                QuestionProviderErrorCode.UNKNOWN_PROVIDER_ERROR,
                retryable=True,
            ) from None
        return combine_question_validation_results(rule_result, decision)


_TARGET_QUESTION_CUES: dict[QuestionTarget, tuple[str, ...]] = {
    QuestionTarget.EVENT: (
        "무슨 일",
        "뭐 했",
        "무엇을 했",
        "어떤 장면",
        "어떤 일이",
    ),
    QuestionTarget.EMOTION: (
        "기분",
        "마음",
        "느낌",
        "어떻게 느꼈",
    ),
    QuestionTarget.EMOTION_REASON: (
        "왜",
        "이유",
        "때문",
        "어떤 점",
    ),
    QuestionTarget.MEMORABLE_MOMENT: (
        "기억",
        "떠오르",
        "생각나는 장면",
        "생각나는 순간",
    ),
}


def _question_matches_target(
    question: str,
    target: QuestionTarget,
) -> bool:
    normalized = "".join(question.casefold().split())
    return any(
        "".join(cue.casefold().split()) in normalized
        for cue in _TARGET_QUESTION_CUES[target]
    )


def _extract_final_question(message: str) -> str:
    question_start = max(message.rfind(". "), message.rfind("! "))
    if question_start < 0:
        return message
    return message[question_start + 2 :]


_CHILD_QUESTION_BRIDGE_CUES: tuple[str, ...] = (
    "그 얘기",
    "그 이야기",
    "얘기도",
    "이야기도",
    "얘기를 하니",
    "이야기를 하니",
    "말하니까",
    "말을 들으니",
    "그러고 보니",
    "궁금해졌",
    "아까",
    "그럼",
    "그러면",
    "그런데",
    "이번에는",
    "돌아가",
)


def _has_child_question_bridge(message: str) -> bool:
    first_boundary = min(
        (
            boundary
            for marker in (". ", "! ", "。", "！")
            if (boundary := message.find(marker)) >= 0
        ),
        default=-1,
    )
    if first_boundary < 0:
        return False
    continuation = message[first_boundary + 1 :].casefold()
    return any(cue in continuation for cue in _CHILD_QUESTION_BRIDGE_CUES)
