"""Stateless orchestration for one text-based diary dialogue turn."""

import logging
from enum import StrEnum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StrictInt,
    field_validator,
    model_validator,
)

from app.dialogue.gms_adapters import (
    GmsCompletionClient,
    GmsGeneratedQuestionValidator,
    GmsQuestionGenerationProvider,
)
from app.dialogue.question_fallback import (
    QuestionGenerationExecutionPolicy,
    QuestionGenerationFailureCode,
    QuestionGenerationLogContext,
    QuestionGenerationSource,
    QuestionGenerationStatus,
)
from app.dialogue.question_generation import generate_question_with_fallback
from app.dialogue.question_prompts import DiaryQuestionGenerationRequest
from app.dialogue.recovery_policy import (
    DEFAULT_NO_RESPONSE_RECOVERY_POLICY,
    evaluate_dialogue_policy,
)
from app.dialogue.schemas import (
    DiaryAnswerAnalysisRequest,
    DiarySlots,
    GameContext,
)
from app.dialogue.slot_analysis import (
    SlotAnalysisError,
    SlotAnalysisLogContext,
    analyze_answer_with_retry,
)
from app.dialogue.termination_policy import (
    ChildTurnSignal,
    DialoguePolicyRequest,
    DialogueTerminationPolicy,
    DialogueTerminationReason,
)


class DialogueTurnDecision(StrEnum):
    """Whether Spring should persist a next question or terminate."""

    CONTINUE = "CONTINUE"
    TERMINATE = "TERMINATE"


class DialoguePipelineFailureCode(StrEnum):
    """Safe failures exposed by the internal FastAPI boundary."""

    SLOT_ANALYSIS_FAILED = "slotAnalysisFailed"
    QUESTION_GENERATION_FAILED = "questionGenerationFailed"


class DialoguePipelineError(Exception):
    """Pipeline failure without child or model text."""

    def __init__(self, code: DialoguePipelineFailureCode) -> None:
        super().__init__(code.value)
        self.code = code


class DialogueTurnRequest(BaseModel):
    """Complete immutable state needed to process one child text turn."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        frozen=True,
    )

    request_id: str = Field(alias="requestId")
    conversation_id: str = Field(alias="conversationId")
    child_answer: str = Field(
        alias="childAnswer",
        min_length=1,
        max_length=2000,
        repr=False,
    )
    current_question: str | None = Field(
        default=None,
        alias="currentQuestion",
        repr=False,
    )
    current_slots: DiarySlots = Field(alias="currentSlots", repr=False)
    recent_questions: tuple[str, ...] = Field(
        default_factory=tuple,
        alias="recentQuestions",
        repr=False,
    )
    game_context: GameContext | None = Field(
        default=None,
        alias="gameContext",
        repr=False,
    )
    question_count: StrictInt = Field(alias="questionCount", ge=0)
    max_question_count: StrictInt = Field(alias="maxQuestionCount", gt=0)

    @field_validator("request_id", "conversation_id", "child_answer")
    @classmethod
    def strip_required_text(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must contain non-whitespace text")
        return stripped

    @field_validator("current_question")
    @classmethod
    def strip_optional_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        stripped = value.strip()
        if not stripped:
            raise ValueError("must contain non-whitespace text")
        return stripped

    @field_validator("recent_questions")
    @classmethod
    def normalize_recent_questions(
        cls,
        value: tuple[str, ...],
    ) -> tuple[str, ...]:
        normalized = tuple(question.strip() for question in value)
        if any(not question for question in normalized):
            raise ValueError("recent questions must contain text")
        return normalized

    @model_validator(mode="after")
    def validate_question_count(self) -> "DialogueTurnRequest":
        if self.question_count > self.max_question_count:
            raise ValueError("questionCount cannot exceed maxQuestionCount")
        return self


class DialogueTurnResponse(BaseModel):
    """Validated internal result persisted by Spring."""

    model_config = ConfigDict(
        extra="forbid",
        populate_by_name=True,
        frozen=True,
    )

    updated_slots: DiarySlots = Field(alias="updatedSlots", repr=False)
    decision: DialogueTurnDecision
    termination_reason: DialogueTerminationReason | None = Field(
        default=None,
        alias="terminationReason",
    )
    assistant_question: str | None = Field(
        default=None,
        alias="assistantQuestion",
        repr=False,
    )
    question_generation_status: QuestionGenerationStatus | None = Field(
        default=None,
        alias="questionGenerationStatus",
    )
    question_generation_source: QuestionGenerationSource | None = Field(
        default=None,
        alias="questionGenerationSource",
    )
    question_generation_failure_code: (
        QuestionGenerationFailureCode | None
    ) = Field(
        default=None,
        alias="questionGenerationFailureCode",
    )

    @model_validator(mode="after")
    def validate_decision_fields(self) -> "DialogueTurnResponse":
        if self.decision is DialogueTurnDecision.TERMINATE:
            if (
                self.termination_reason is None
                or self.assistant_question is not None
                or self.question_generation_status is not None
                or self.question_generation_source is not None
                or self.question_generation_failure_code is not None
            ):
                raise ValueError("terminal response fields are inconsistent")
            return self
        if (
            self.termination_reason is not None
            or self.assistant_question is None
            or not self.assistant_question.strip()
            or self.question_generation_status
            not in {
                QuestionGenerationStatus.GENERATED,
                QuestionGenerationStatus.FALLBACK_USED,
            }
            or self.question_generation_source is None
        ):
            raise ValueError("continue response fields are inconsistent")
        return self


class DialogueTurnPipeline:
    """Connect T09 through T14 without retaining request state."""

    def __init__(
        self,
        *,
        client: GmsCompletionClient,
        execution_policy: QuestionGenerationExecutionPolicy,
        termination_policy: DialogueTerminationPolicy,
        logger: logging.Logger,
    ) -> None:
        self._client = client
        self._execution_policy = execution_policy
        self._termination_policy = termination_policy
        self._logger = logger
        self._question_provider = GmsQuestionGenerationProvider(client)
        self._question_validator = GmsGeneratedQuestionValidator(client)

    async def process(
        self,
        request: DialogueTurnRequest,
    ) -> DialogueTurnResponse:
        try:
            updated_slots = await analyze_answer_with_retry(
                DiaryAnswerAnalysisRequest(
                    current_question=request.current_question,
                    child_answer=request.child_answer,
                    current_slots=request.current_slots,
                    game_context=request.game_context,
                ),
                self._client,
                self._execution_policy,
                SlotAnalysisLogContext(
                    conversation_id=request.conversation_id,
                    request_id=request.request_id,
                ),
                self._logger,
            )
        except SlotAnalysisError:
            raise DialoguePipelineError(
                DialoguePipelineFailureCode.SLOT_ANALYSIS_FAILED
            ) from None

        policy_decision = evaluate_dialogue_policy(
            DialoguePolicyRequest(
                slots=updated_slots,
                question_count=request.question_count,
                max_question_count=request.max_question_count,
                turn_signal=ChildTurnSignal.ANSWERED,
            ),
            self._termination_policy,
            DEFAULT_NO_RESPONSE_RECOVERY_POLICY,
        )
        if policy_decision.should_terminate:
            return DialogueTurnResponse(
                updated_slots=updated_slots,
                decision=DialogueTurnDecision.TERMINATE,
                termination_reason=policy_decision.termination_reason,
            )

        target = policy_decision.next_target
        if target is None:
            raise DialoguePipelineError(
                DialoguePipelineFailureCode.QUESTION_GENERATION_FAILED
            )
        generation_result = await generate_question_with_fallback(
            DiaryQuestionGenerationRequest(
                target=target,
                current_slots=updated_slots,
                recent_questions=request.recent_questions,
                game_context=request.game_context,
            ),
            self._question_provider,
            self._question_validator,
            self._execution_policy,
            QuestionGenerationLogContext(
                conversation_id=request.conversation_id,
                turn_id=request.request_id,
                response_id=None,
                provider_name="gms",
            ),
            self._logger,
        )
        if (
            generation_result.status is QuestionGenerationStatus.FAILED
            or generation_result.question is None
        ):
            raise DialoguePipelineError(
                DialoguePipelineFailureCode.QUESTION_GENERATION_FAILED
            )
        return DialogueTurnResponse(
            updated_slots=updated_slots,
            decision=DialogueTurnDecision.CONTINUE,
            assistant_question=generation_result.question,
            question_generation_status=generation_result.status,
            question_generation_source=generation_result.source,
            question_generation_failure_code=generation_result.failure_code,
        )
