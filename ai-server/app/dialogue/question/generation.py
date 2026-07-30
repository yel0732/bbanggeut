"""Run timeout, retry, validation, and fallback question generation."""

import asyncio
import logging
import time
from dataclasses import dataclass

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
from app.dialogue.question.candidate import (
    GeneratedDialogueTurnParseError,
    parse_generated_dialogue_turn,
)


@dataclass(frozen=True)
class _CandidateValidationFailure:
    failure_code: QuestionGenerationFailureCode
    retryable: bool
    issues: tuple[str, ...] = ()


async def generate_question_with_fallback(
    request: DiaryQuestionGenerationRequest,
    provider: QuestionGenerationProvider,
    validator: GeneratedQuestionValidator,
    policy: QuestionGenerationExecutionPolicy,
    log_context: QuestionGenerationLogContext,
    logger: logging.Logger,
    *,
    fallback_text: str | None = None,
) -> QuestionGenerationExecutionResult:
    """Generate and validate one question, then use a trusted fallback."""

    execution_started = time.perf_counter()
    prompt = build_question_generation_prompt(request)
    failure_code = QuestionGenerationFailureCode.UNEXPECTED_PROVIDER_FAILURE
    attempts = 0

    for attempt in range(1, policy.max_attempts + 1):
        attempts = attempt
        generation_started = time.perf_counter()
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
                elapsed_ms=_elapsed_ms(generation_started),
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
                elapsed_ms=_elapsed_ms(generation_started),
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
                elapsed_ms=_elapsed_ms(generation_started),
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

            failure_code = validation_outcome.failure_code
            if validation_outcome.issues:
                prompt = build_question_generation_prompt(
                    request,
                    retry_issues=validation_outcome.issues,
                )
            if not validation_outcome.retryable:
                break

        if attempt >= policy.max_attempts:
            break

    fallback = (
        fallback_text.strip()
        if fallback_text is not None and fallback_text.strip()
        else build_fallback_question(request.target).text
    )
    fallback_validation = validate_question_rules(
        QuestionRuleValidationRequest(
            question=fallback,
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
            elapsed_ms=_elapsed_ms(execution_started),
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
        elapsed_ms=_elapsed_ms(execution_started),
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
    | _CandidateValidationFailure
):
    validation_started = time.perf_counter()
    try:
        parsed_candidate = parse_generated_dialogue_turn(
            candidate,
            expected_target=request.target,
            child_answer=request.child_answer,
            bridge_required=request.redirect_context is not None,
        )
        async with asyncio.timeout(policy.timeout_seconds):
            result = await validator.validate_question(
                request,
                parsed_candidate.message,
            )
    except asyncio.CancelledError:
        raise
    except GeneratedDialogueTurnParseError as error:
        outcome = _CandidateValidationFailure(
            QuestionGenerationFailureCode.GENERATED_QUESTION_REJECTED,
            True,
            (error.code.value,),
        )
    except TimeoutError:
        outcome = _CandidateValidationFailure(
            QuestionGenerationFailureCode.TIMEOUT,
            True,
        )
    except QuestionProviderError as error:
        outcome = _CandidateValidationFailure(
            _provider_failure_code(error),
            error.retryable,
        )
    except Exception:
        outcome = _CandidateValidationFailure(
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
            outcome = _CandidateValidationFailure(
                QuestionGenerationFailureCode.GENERATED_QUESTION_REJECTED,
                True,
                tuple(issue.code.value for issue in result.issues),
            )
        else:
            outcome = _CandidateValidationFailure(
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
        outcome.failure_code,
        elapsed_ms=_elapsed_ms(validation_started),
        validation_issues=outcome.issues,
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
    elapsed_ms: int,
    validation_issues: tuple[str, ...] = (),
    level: int = logging.WARNING,
) -> None:
    issue_summary = ",".join(validation_issues) if validation_issues else "-"
    logger.log(
        level,
        (
            "question generation attempt failed: "
            "stage=%s code=%s issues=%s attempt=%d/%d "
            "target=%s elapsedMs=%d"
        ),
        stage,
        failure_code.value,
        issue_summary,
        attempt,
        policy.max_attempts,
        request.target.value,
        elapsed_ms,
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
            "validation_issues": validation_issues,
            "elapsed_ms": elapsed_ms,
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
    *,
    elapsed_ms: int,
) -> None:
    logger.warning(
        (
            "question generation fallback used: "
            "code=%s attempts=%d/%d target=%s elapsedMs=%d"
        ),
        failure_code.value,
        attempts,
        policy.max_attempts,
        request.target.value,
        elapsed_ms,
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
            "elapsed_ms": elapsed_ms,
            "fallback_used": True,
        },
    )


def _elapsed_ms(started: float) -> int:
    return max(0, round((time.perf_counter() - started) * 1000))
