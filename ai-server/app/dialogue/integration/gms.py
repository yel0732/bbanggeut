"""Adapt GMS completions to dialogue-domain contracts."""

from typing import Protocol

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
                max_characters=(
                    80 if request.redirect_context is not None else 50
                ),
                allow_leading_statement=(
                    request.redirect_context is not None
                ),
            ),
        )
        if not rule_result.is_valid:
            return combine_question_validation_results(rule_result, None)
        if (
            request.redirect_context is not None
            and request.redirect_context.child_question
            and not rule_result.normalized_question.startswith("나는")
        ):
            return QuestionValidationResult(
                normalized_question=rule_result.normalized_question,
                issues=(
                    QuestionValidationIssue(
                        QuestionValidationCode.TARGET_MISMATCH
                    ),
                ),
            )

        semantic_prompt = build_semantic_question_validation_prompt(
            SemanticQuestionValidationRequest(
                target=request.target,
                question=rule_result.normalized_question,
                current_slots=request.current_slots,
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
