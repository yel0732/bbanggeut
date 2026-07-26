"""Run timeout, retry, validation, and fallback question generation."""

import asyncio
import logging

from app.dialogue.question.fallback import (
    QuestionGenerationExecutionPolicy,
    QuestionGenerationExecutionResult,
    QuestionGenerationFailureCode,
    QuestionGenerationLogContext,
    QuestionGenerationSource,
    QuestionGenerationStatus,
    build_fallback_question,
)
from app.dialogue.question.prompts import (
    DiaryQuestionGenerationRequest,
    build_question_generation_prompt,
)
from app.dialogue.question.provider import (
    GeneratedQuestionValidator,
    QuestionGenerationProvider,
    QuestionProviderError,
)
from app.dialogue.question.validator import (
    QuestionRuleValidationRequest,
    QuestionValidationResult,
    validate_question_rules,
)


async def generate_question_with_fallback(
    request: DiaryQuestionGenerationRequest,
    provider: QuestionGenerationProvider,
    validator: GeneratedQuestionValidator,
    policy: QuestionGenerationExecutionPolicy,
    log_context: QuestionGenerationLogContext,
    logger: logging.Logger,
) -> QuestionGenerationExecutionResult:
    """Generate and validate one question, then use a trusted fallback."""

    prompt = build_question_generation_prompt(request)
    failure_code = QuestionGenerationFailureCode.UNEXPECTED_PROVIDER_FAILURE
    attempts = 0

    for attempt in range(1, policy.max_attempts + 1):
        attempts = attempt
        try:
            async with asyncio.timeout(policy.timeout_seconds):
                candidate = await provider.generate_question(prompt)
        except asyncio.CancelledError:
            raise
        except TimeoutError:
            failure_code = QuestionGenerationFailureCode.TIMEOUT
            _log_failure(
                logger,
                log_context,
                request,
                attempt,
                policy,
                "generation",
                failure_code,
            )
        except QuestionProviderError as error:
            failure_code = _provider_failure_code(error)
            _log_failure(
                logger,
                log_context,
                request,
                attempt,
                policy,
                "generation",
                failure_code,
            )
            if not error.retryable:
                break
        except Exception:
            failure_code = (
                QuestionGenerationFailureCode.UNEXPECTED_PROVIDER_FAILURE
            )
            _log_failure(
                logger,
                log_context,
                request,
                attempt,
                policy,
                "generation",
                failure_code,
            )
            break
        else:
            validation_outcome = await _validate_candidate(
                request=request,
                candidate=candidate,
                validator=validator,
                policy=policy,
                log_context=log_context,
                logger=logger,
                attempt=attempt,
            )
            if isinstance(validation_outcome, QuestionValidationResult):
                return QuestionGenerationExecutionResult(
                    status=QuestionGenerationStatus.GENERATED,
                    source=QuestionGenerationSource.LLM,
                    question=validation_outcome.normalized_question,
                    attempts=attempt,
                    failure_code=None,
                )

            failure_code, retryable = validation_outcome
            if not retryable:
                break

        if attempt >= policy.max_attempts:
            break

    fallback = build_fallback_question(request.target)
    fallback_validation = validate_question_rules(
        QuestionRuleValidationRequest(
            question=fallback.text,
            recent_questions=request.recent_questions,
        )
    )
    if fallback_validation.is_valid:
        _log_fallback_used(
            logger,
            log_context,
            request,
            attempts,
            policy,
            failure_code,
        )
        return QuestionGenerationExecutionResult(
            status=QuestionGenerationStatus.FALLBACK_USED,
            source=QuestionGenerationSource.RULE_BASED_FALLBACK,
            question=fallback_validation.normalized_question,
            attempts=attempts,
            failure_code=failure_code,
        )

    _log_failure(
        logger,
        log_context,
        request,
        attempts,
        policy,
        "fallbackValidation",
        QuestionGenerationFailureCode.FALLBACK_VALIDATION_FAILED,
        level=logging.ERROR,
    )
    return QuestionGenerationExecutionResult(
        status=QuestionGenerationStatus.FAILED,
        source=None,
        question=None,
        attempts=attempts,
        failure_code=(
            QuestionGenerationFailureCode.FALLBACK_VALIDATION_FAILED
        ),
    )


async def _validate_candidate(
    *,
    request: DiaryQuestionGenerationRequest,
    candidate: str,
    validator: GeneratedQuestionValidator,
    policy: QuestionGenerationExecutionPolicy,
    log_context: QuestionGenerationLogContext,
    logger: logging.Logger,
    attempt: int,
) -> (
    QuestionValidationResult
    | tuple[QuestionGenerationFailureCode, bool]
):
    try:
        async with asyncio.timeout(policy.timeout_seconds):
            result = await validator.validate_question(request, candidate)
    except asyncio.CancelledError:
        raise
    except TimeoutError:
        outcome = (QuestionGenerationFailureCode.TIMEOUT, True)
    except QuestionProviderError as error:
        outcome = (_provider_failure_code(error), error.retryable)
    except Exception:
        outcome = (
            QuestionGenerationFailureCode.UNEXPECTED_PROVIDER_FAILURE,
            False,
        )
    else:
        if (
            isinstance(result, QuestionValidationResult)
            and result.is_valid
            and bool(result.normalized_question.strip())
        ):
            return result
        if isinstance(result, QuestionValidationResult):
            outcome = (
                QuestionGenerationFailureCode.GENERATED_QUESTION_REJECTED,
                True,
            )
        else:
            outcome = (
                QuestionGenerationFailureCode.UNEXPECTED_PROVIDER_FAILURE,
                False,
            )

    _log_failure(
        logger,
        log_context,
        request,
        attempt,
        policy,
        "validation",
        outcome[0],
    )
    return outcome


def _provider_failure_code(
    error: QuestionProviderError,
) -> QuestionGenerationFailureCode:
    if error.retryable:
        return QuestionGenerationFailureCode.PROVIDER_TEMPORARY_FAILURE
    return QuestionGenerationFailureCode.PROVIDER_PERMANENT_FAILURE


def _log_failure(
    logger: logging.Logger,
    context: QuestionGenerationLogContext,
    request: DiaryQuestionGenerationRequest,
    attempt: int,
    policy: QuestionGenerationExecutionPolicy,
    stage: str,
    failure_code: QuestionGenerationFailureCode,
    *,
    level: int = logging.WARNING,
) -> None:
    logger.log(
        level,
        "question generation attempt failed",
        extra={
            "conversation_id": context.conversation_id,
            "turn_id": context.turn_id,
            "response_id": context.response_id,
            "provider_name": context.provider_name,
            "target": request.target.value,
            "attempt": attempt,
            "max_attempts": policy.max_attempts,
            "failure_stage": stage,
            "failure_code": failure_code.value,
            "fallback_used": stage == "fallbackValidation",
        },
    )


def _log_fallback_used(
    logger: logging.Logger,
    context: QuestionGenerationLogContext,
    request: DiaryQuestionGenerationRequest,
    attempts: int,
    policy: QuestionGenerationExecutionPolicy,
    failure_code: QuestionGenerationFailureCode,
) -> None:
    logger.warning(
        "question generation fallback used",
        extra={
            "conversation_id": context.conversation_id,
            "turn_id": context.turn_id,
            "response_id": context.response_id,
            "provider_name": context.provider_name,
            "target": request.target.value,
            "attempt": attempts,
            "max_attempts": policy.max_attempts,
            "failure_stage": "fallback",
            "failure_code": failure_code.value,
            "fallback_used": True,
        },
    )
