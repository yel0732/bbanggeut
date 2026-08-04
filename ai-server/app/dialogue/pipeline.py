"""Stateless orchestration for one text-based diary dialogue turn."""

import asyncio
import logging
from collections.abc import Sequence
from enum import StrEnum

from pydantic import (
    AliasChoices,
    BaseModel,
    ConfigDict,
    Field,
    StrictInt,
    field_validator,
    model_validator,
)

from app.dialogue.integration.gms import (
    GmsCompletionClient,
    GmsGeneratedQuestionValidator,
    GmsQuestionGenerationProvider,
)
from app.dialogue.analysis.answer_relevance import (
    AnswerRelevanceDecision,
    AnswerRelevanceLogContext,
    AnswerRelevanceRequest,
    TurnControlAction,
    classify_answer_relevance_with_retry,
)
from app.dialogue.question.fallback import (
    QuestionGenerationExecutionPolicy,
    QuestionGenerationFailureCode,
    QuestionGenerationLogContext,
    QuestionGenerationSource,
    QuestionGenerationStatus,
)
from app.dialogue.question.generation import generate_question_with_fallback
from app.dialogue.question.prompts import (
    DiaryQuestionGenerationRequest,
    QuestionRedirectContext,
)
from app.dialogue.policy.recovery import (
    ASK_TO_REPEAT_PROMPT,
    CHOICE_QUESTION_BY_TARGET,
    DEFAULT_NO_RESPONSE_RECOVERY_POLICY,
    SIMPLIFIED_QUESTION_BY_TARGET,
    evaluate_dialogue_policy,
)
from app.dialogue.response_style import (
    acknowledge_child_answer,
    build_fallback_assistant_message,
    closing_message,
    fallback_question_for_target,
    optional_final_question,
    optional_final_message,
    safe_child_question_reply,
    soft_continue_message,
    soft_continue_question,
)
from app.dialogue.safety import detect_dialogue_safety
from app.dialogue.schemas import (
    DialogueTopic,
    DialogueTopicContext,
    DialogueTopicHistoryItem,
    DiaryAnswerAnalysisRequest,
    DiarySlots,
    GameContext,
)
from app.dialogue.analysis.service import (
    SlotAnalysisError,
    SlotAnalysisFailureCode,
    SlotAnalysisLogContext,
    analyze_answer_with_retry,
)
from app.dialogue.analysis.enrichment import enrich_explicit_people
from app.dialogue.policy.target import (
    QUESTION_TARGET_SLOT_FIELDS,
    QuestionTarget,
    select_next_question_target,
)
from app.dialogue.policy.stop_intent import is_stop_request
from app.dialogue.policy.termination import (
    ChildTurnSignal,
    DialoguePolicyRequest,
    DialogueTerminationPolicy,
    DialogueTerminationReason,
)
from app.dialogue.turn_understanding import (
    apply_turn_understanding,
    understand_child_turn,
)


class DialogueTurnDecision(StrEnum):
    """How Spring should persist the current topic result."""

    CONTINUE_TOPIC = "CONTINUE_TOPIC"
    SWITCH_TOPIC = "SWITCH_TOPIC"
    TERMINATE_SESSION = "TERMINATE_SESSION"

    # Source compatibility for existing Python callers.
    CONTINUE = "CONTINUE_TOPIC"
    TERMINATE = "TERMINATE_SESSION"


class DialoguePipelineFailureCode(StrEnum):
    """Safe failures exposed by the internal FastAPI boundary."""

    SLOT_ANALYSIS_FAILED = "slotAnalysisFailed"
    QUESTION_GENERATION_FAILED = "questionGenerationFailed"


class DialoguePipelineError(Exception):
    """Pipeline failure without child or model text."""

    def __init__(self, code: DialoguePipelineFailureCode) -> None:
        super().__init__(code.value)
        self.code = code


class DialogueOpeningRequest(BaseModel):
    """Minimal Spring snapshot needed for the deterministic first question."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        frozen=True,
    )

    current_topic: DialogueTopic = Field(alias="currentTopic")
    topic_context: DialogueTopicContext = Field(alias="topicContext")

    @model_validator(mode="after")
    def validate_topic_context(self) -> "DialogueOpeningRequest":
        if self.topic_context.topic is not self.current_topic:
            raise ValueError("topicContext.topic must match currentTopic")
        return self


class DialogueOpeningResponse(BaseModel):
    """Text-only opening turn shared by REST harness and realtime sessions."""

    model_config = ConfigDict(
        populate_by_name=True,
        frozen=True,
    )

    assistant_message: str = Field(alias="assistantMessage")
    assistant_question: str = Field(alias="assistantQuestion")
    assistant_target: QuestionTarget = Field(alias="assistantTarget")


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
    # childAnswer가 음성 인식(STT) 결과일 때의 신뢰도. 텍스트 턴(Spring 경유)은
    # 보내지 않으며 None으로 남는다 — 하위 호환 옵션 필드.
    stt_confidence: float | None = Field(
        default=None,
        alias="sttConfidence",
        ge=0.0,
        le=1.0,
    )
    current_question: str | None = Field(
        default=None,
        alias="currentQuestion",
        repr=False,
    )
    current_target: QuestionTarget | None = Field(
        default=None,
        alias="currentTarget",
    )
    current_topic: DialogueTopic = Field(
        default=DialogueTopic.DAILY,
        alias="currentTopic",
    )
    next_topic: DialogueTopic | None = Field(
        default=None,
        alias="nextTopic",
    )
    current_slots: DiarySlots = Field(
        alias="currentTopicSlots",
        validation_alias=AliasChoices("currentTopicSlots", "currentSlots"),
        repr=False,
    )
    recent_questions: tuple[str, ...] = Field(
        default_factory=tuple,
        alias="recentQuestions",
        repr=False,
    )
    topic_history: tuple[DialogueTopicHistoryItem, ...] = Field(
        default_factory=tuple,
        alias="topicHistory",
        repr=False,
    )
    game_context: GameContext | DialogueTopicContext | None = Field(
        default=None,
        alias="topicContext",
        validation_alias=AliasChoices("topicContext", "gameContext"),
        repr=False,
    )
    question_count: StrictInt = Field(
        alias="topicQuestionCount",
        validation_alias=AliasChoices("topicQuestionCount", "questionCount"),
        ge=0,
    )
    max_question_count: StrictInt = Field(
        alias="topicMaxQuestionCount",
        validation_alias=AliasChoices(
            "topicMaxQuestionCount",
            "maxQuestionCount",
        ),
        gt=0,
    )
    # 무응답 사다리에서 건너뛰기로 확정된 질문 목표. 목표 추정·다음 질문 선택·
    # backfill에서 제외해 같은 목표를 다시 묻거나 답변을 오배정하지 않게 한다.
    # 텍스트 턴(Spring 경유)은 건너뛰기 개념이 없어 빈 값으로 남는다.
    skipped_targets: frozenset[QuestionTarget] = Field(
        default_factory=frozenset,
        alias="skippedTargets",
    )

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
            raise ValueError(
                "topicQuestionCount cannot exceed topicMaxQuestionCount"
            )
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
    assistant_message: str | None = Field(
        default=None,
        alias="assistantMessage",
        repr=False,
    )
    assistant_question: str | None = Field(
        default=None,
        alias="assistantQuestion",
        repr=False,
    )
    assistant_target: QuestionTarget | None = Field(
        default=None,
        alias="assistantTarget",
    )
    updated_skipped_targets: frozenset[QuestionTarget] | None = Field(
        default=None,
        alias="updatedSkippedTargets",
    )
    closing_message: str | None = Field(
        default=None,
        alias="closingMessage",
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
        if self.decision is DialogueTurnDecision.TERMINATE_SESSION:
            if (
                self.termination_reason is None
                or self.assistant_message is not None
                or self.assistant_question is not None
                or self.assistant_target is not None
                or self.question_generation_status is not None
                or self.question_generation_source is not None
                or self.question_generation_failure_code is not None
            ):
                raise ValueError("terminal response fields are inconsistent")
            return self
        if (
            (
                self.decision is DialogueTurnDecision.CONTINUE_TOPIC
                and self.termination_reason is not None
            )
            or (
                self.decision is DialogueTurnDecision.SWITCH_TOPIC
                and self.termination_reason is None
            )
            or self.assistant_message is None
            or not self.assistant_message.strip()
            or self.assistant_question is None
            or not self.assistant_question.strip()
            or self.question_generation_status
            not in {
                QuestionGenerationStatus.GENERATED,
                QuestionGenerationStatus.TEMPLATE_USED,
                QuestionGenerationStatus.FALLBACK_USED,
            }
            or self.question_generation_source is None
            or self.closing_message is not None
        ):
            raise ValueError("continue response fields are inconsistent")
        return self


class DialogueTurnPipeline:
    """Connect T09 through T14 without retaining request state."""

    def __init__(
        self,
        *,
        analysis_client: GmsCompletionClient,
        response_client: GmsCompletionClient,
        execution_policy: QuestionGenerationExecutionPolicy,
        termination_policy: DialogueTerminationPolicy,
        logger: logging.Logger,
    ) -> None:
        self._analysis_client = analysis_client
        self._response_client = response_client
        self._execution_policy = execution_policy
        self._termination_policy = termination_policy
        self._logger = logger

    async def process(
        self,
        request: DialogueTurnRequest,
    ) -> DialogueTurnResponse:
        if is_stop_request(request.child_answer):
            # 아이의 명시적 종료 의사는 모든 정책보다 우선한다(§9). 다음 주제가
            # 남아 있어도 전환하지 않고 즉시 종료하며, 종료 문구에 슬롯 분석
            # LLM을 돌리지 않는다. COMPLETED로 종료되므로 그때까지 채운 슬롯으로
            # 일기 생성이 가능하다.
            return DialogueTurnResponse(
                updated_slots=request.current_slots.model_copy(deep=True),
                decision=DialogueTurnDecision.TERMINATE_SESSION,
                termination_reason=(
                    DialogueTerminationReason.CHILD_REQUESTED_STOP
                ),
                updated_skipped_targets=request.skipped_targets,
                closing_message=CONVERSATION_CLOSING_MESSAGE,
            )

        safety_decision = detect_dialogue_safety(request.child_answer)
        if safety_decision is not None:
            return DialogueTurnResponse(
                updated_slots=request.current_slots.model_copy(deep=True),
                decision=DialogueTurnDecision.CONTINUE_TOPIC,
                assistant_message=safety_decision.assistant_message,
                assistant_question=safety_decision.assistant_question,
                assistant_target=QuestionTarget.EVENT,
                updated_skipped_targets=request.skipped_targets,
                question_generation_status=(
                    QuestionGenerationStatus.TEMPLATE_USED
                ),
                question_generation_source=(
                    QuestionGenerationSource.RULE_BASED_TEMPLATE
                ),
            )

        asked_target = _current_unanswered_target(request)
        available_targets = _available_question_targets(
            request.current_slots,
            request.skipped_targets,
        )
        slot_analysis = analyze_answer_with_retry(
            DiaryAnswerAnalysisRequest(
                current_question=request.current_question,
                child_answer=request.child_answer,
                stt_confidence=request.stt_confidence,
                current_slots=request.current_slots,
                topic_history=request.topic_history,
                game_context=request.game_context,
            ),
            self._analysis_client,
            self._execution_policy,
            SlotAnalysisLogContext(
                conversation_id=request.conversation_id,
                request_id=request.request_id,
            ),
            self._logger,
        )
        relevance_decision: AnswerRelevanceDecision | None = None
        try:
            if request.current_question is None:
                updated_slots = await slot_analysis
            else:
                updated_slots, relevance_decision = await asyncio.gather(
                    slot_analysis,
                    classify_answer_relevance_with_retry(
                        AnswerRelevanceRequest(
                            current_question=request.current_question,
                            child_answer=request.child_answer,
                            current_slots=request.current_slots,
                            topic_history=request.topic_history,
                            current_target=asked_target,
                            available_targets=available_targets,
                            next_topic_available=(
                                request.next_topic is not None
                            ),
                        ),
                        self._analysis_client,
                        self._execution_policy,
                        AnswerRelevanceLogContext(
                            conversation_id=request.conversation_id,
                            request_id=request.request_id,
                        ),
                        self._logger,
                    ),
                )
        except SlotAnalysisError as error:
            recoverable_target = _recoverable_target_for_slot_analysis(
                request,
                error,
            )
            if recoverable_target is not None:
                updated_slots = _backfill_answered_target(
                    slots=request.current_slots.model_copy(deep=True),
                    target=recoverable_target,
                    child_answer=request.child_answer,
                )
            else:
                raise DialoguePipelineError(
                    DialoguePipelineFailureCode.SLOT_ANALYSIS_FAILED
                ) from None

        if (
            relevance_decision is not None
            and relevance_decision.control_action
            is TurnControlAction.END_SESSION
        ):
            return DialogueTurnResponse(
                updated_slots=request.current_slots.model_copy(deep=True),
                decision=DialogueTurnDecision.TERMINATE_SESSION,
                termination_reason=(
                    DialogueTerminationReason.CHILD_REQUESTED_STOP
                ),
                updated_skipped_targets=request.skipped_targets,
                closing_message=CONVERSATION_CLOSING_MESSAGE,
            )
        if (
            relevance_decision is not None
            and relevance_decision.control_action
            is TurnControlAction.SWITCH_TOPIC
        ):
            if request.next_topic is None:
                return DialogueTurnResponse(
                    updated_slots=request.current_slots.model_copy(deep=True),
                    decision=DialogueTurnDecision.TERMINATE_SESSION,
                    termination_reason=(
                        DialogueTerminationReason.CHILD_REQUESTED_STOP
                    ),
                    updated_skipped_targets=request.skipped_targets,
                    closing_message=CONVERSATION_CLOSING_MESSAGE,
                )
            transition_question = _topic_opening_question(
                request.next_topic
            )
            return DialogueTurnResponse(
                updated_slots=request.current_slots.model_copy(deep=True),
                decision=DialogueTurnDecision.SWITCH_TOPIC,
                termination_reason=(
                    DialogueTerminationReason.CHILD_REQUESTED_TOPIC_SWITCH
                ),
                assistant_message=(
                    "알겠어. 이 이야기는 여기까지 하고, "
                    f"{transition_question}"
                ),
                assistant_question=transition_question,
                assistant_target=QuestionTarget.EVENT,
                updated_skipped_targets=request.skipped_targets,
                question_generation_status=(
                    QuestionGenerationStatus.TEMPLATE_USED
                ),
                question_generation_source=(
                    QuestionGenerationSource.RULE_BASED_TEMPLATE
                ),
            )

        if request.current_question is None:
            updated_slots = _normalize_first_turn_slots(
                updated_slots,
                request.child_answer,
                previous_slots=request.current_slots,
            )
        if (
            relevance_decision is not None
            and relevance_decision.answered
        ):
            updated_slots = _backfill_answered_target(
                slots=updated_slots,
                target=asked_target,
                child_answer=request.child_answer,
            )
        updated_skipped_targets = request.skipped_targets
        if (
            relevance_decision is not None
            and relevance_decision.answered is False
        ):
            # 의도된 트레이드오프: 딴 이야기로 판정된 턴의 추출 결과는 통째로 버린다.
            # 무관한 발화("지금 배가 고파")에서 추출된 값이 일기 슬롯을 오염시키는
            # 것을 막는 쪽이, 오판 1회로 같은 질문을 한 번 더 묻는 비용(복구 가능)
            # 보다 낫다. 판정 실패(None)는 이 분기를 타지 않으므로 추출 결과가
            # 보존된다 — LLM 판정 불능이 슬롯 손실로 이어지지 않는다.
            updated_slots = request.current_slots.model_copy(deep=True)
            if (
                relevance_decision.control_action
                is TurnControlAction.SKIP_CURRENT_TARGET
                and asked_target is not None
            ):
                updated_skipped_targets = frozenset(
                    (*request.skipped_targets, asked_target)
                )
        else:
            updated_slots = enrich_explicit_people(
                updated_slots,
                request.child_answer,
            )
        updated_slots = _backfill_contextual_emotion_reason(
            slots=updated_slots,
            request=request,
            asked_target=asked_target,
            relevance_decision=relevance_decision,
        )
        turn_understanding = understand_child_turn(
            child_answer=request.child_answer,
            current_question=request.current_question,
            asked_target=asked_target,
            current_slots=request.current_slots,
        )
        if (
            relevance_decision is None
            or relevance_decision.answered
            or _is_optional_final_question(request.current_question or "")
        ):
            updated_slots = apply_turn_understanding(
                updated_slots,
                turn_understanding,
                excluded_targets=request.skipped_targets,
            )
        if _is_optional_final_question(request.current_question or ""):
            if (
                turn_understanding.continue_request
                and request.question_count < request.max_question_count
            ):
                question = soft_continue_question()
                return DialogueTurnResponse(
                    updated_slots=updated_slots,
                    decision=DialogueTurnDecision.CONTINUE_TOPIC,
                    assistant_message=(
                        f"{safe_child_question_reply(request.child_answer)} "
                        f"{question}"
                    ),
                    assistant_question=question,
                    assistant_target=QuestionTarget.MEMORABLE_MOMENT,
                    updated_skipped_targets=updated_skipped_targets,
                    question_generation_status=(
                        QuestionGenerationStatus.TEMPLATE_USED
                    ),
                    question_generation_source=(
                        QuestionGenerationSource.RULE_BASED_TEMPLATE
                    ),
                )
            return DialogueTurnResponse(
                updated_slots=updated_slots,
                decision=DialogueTurnDecision.TERMINATE_SESSION,
                termination_reason=(
                    DialogueTerminationReason.REQUIRED_SLOTS_FILLED
                ),
                updated_skipped_targets=updated_skipped_targets,
                closing_message=closing_message(
                    child_answer=request.child_answer,
                    child_question=_is_child_question(
                        request,
                        relevance_decision,
                    ),
                    bridge_hint=(
                        None
                        if relevance_decision is None
                        else relevance_decision.bridge_response
                    ),
                ),
            )

        unanswered_target = _resolve_unanswered_target(
            asked_target=asked_target,
            answered_current_question=(
                None
                if relevance_decision is None
                else relevance_decision.answered
            ),
            updated_slots=updated_slots,
        )
        policy_decision = evaluate_dialogue_policy(
            DialoguePolicyRequest(
                slots=updated_slots,
                question_count=request.question_count,
                max_question_count=request.max_question_count,
                turn_signal=ChildTurnSignal.ANSWERED,
                skipped_targets=updated_skipped_targets,
            ),
            self._termination_policy,
            DEFAULT_NO_RESPONSE_RECOVERY_POLICY,
        )
        if policy_decision.should_terminate:
            if _should_offer_optional_final_prompt(
                request=request,
                slots=updated_slots,
                reason=policy_decision.termination_reason,
            ):
                if request.question_count <= 3:
                    question = soft_continue_question()
                    message = soft_continue_message(request.child_answer)
                else:
                    question = optional_final_question()
                    message = optional_final_message(
                        child_answer=request.child_answer,
                        question=question,
                        child_question=_is_child_question(
                            request,
                            relevance_decision,
                        ),
                        bridge_hint=(
                            None
                            if relevance_decision is None
                            else relevance_decision.bridge_response
                        ),
                    )
                return DialogueTurnResponse(
                    updated_slots=updated_slots,
                    decision=DialogueTurnDecision.CONTINUE_TOPIC,
                    assistant_message=message,
                    assistant_question=question,
                    assistant_target=QuestionTarget.MEMORABLE_MOMENT,
                    updated_skipped_targets=updated_skipped_targets,
                    question_generation_status=(
                        QuestionGenerationStatus.TEMPLATE_USED
                    ),
                    question_generation_source=(
                        QuestionGenerationSource.RULE_BASED_TEMPLATE
                    ),
                )
            if request.next_topic is not None:
                transition_question = _topic_opening_question(
                    request.next_topic
                )
                return DialogueTurnResponse(
                    updated_slots=updated_slots,
                    decision=DialogueTurnDecision.SWITCH_TOPIC,
                    termination_reason=policy_decision.termination_reason,
                    assistant_message=_topic_transition_message(
                        request.current_topic,
                        request.next_topic,
                        transition_question,
                    ),
                    assistant_question=transition_question,
                    assistant_target=QuestionTarget.EVENT,
                    updated_skipped_targets=updated_skipped_targets,
                    question_generation_status=(
                        QuestionGenerationStatus.TEMPLATE_USED
                    ),
                    question_generation_source=(
                        QuestionGenerationSource.RULE_BASED_TEMPLATE
                    ),
                )
            return DialogueTurnResponse(
                updated_slots=updated_slots,
                decision=DialogueTurnDecision.TERMINATE_SESSION,
                termination_reason=policy_decision.termination_reason,
                updated_skipped_targets=updated_skipped_targets,
                closing_message=closing_message(
                    child_answer=request.child_answer,
                    child_question=_is_child_question(
                        request,
                        relevance_decision,
                    ),
                    bridge_hint=(
                        None
                        if relevance_decision is None
                        else relevance_decision.bridge_response
                    ),
                ),
            )

        target = _select_contextual_target(
            suggested_target=(
                None
                if relevance_decision is None
                else relevance_decision.suggested_target
            ),
            fallback_target=policy_decision.next_target,
            slots=updated_slots,
            skipped_targets=frozenset(
                (*updated_skipped_targets, *turn_understanding.suppressed_targets)
            ),
        )
        if target is None:
            raise DialoguePipelineError(
                DialoguePipelineFailureCode.QUESTION_GENERATION_FAILED
            )
        redirect_context = _build_redirect_context(
            request=request,
            target=target,
            unanswered_target=unanswered_target,
            relevance_decision=relevance_decision,
        )
        fallback_question = _topic_fallback_question(
            request.current_topic,
            target,
            request.game_context,
        )
        generated = await generate_question_with_fallback(
            DiaryQuestionGenerationRequest(
                target=target,
                current_topic=request.current_topic,
                child_answer=request.child_answer,
                current_question=request.current_question,
                current_slots=updated_slots,
                topic_history=request.topic_history,
                recent_questions=request.recent_questions,
                game_context=request.game_context,
                redirect_context=redirect_context,
                selective_semantic_validation=True,
            ),
            GmsQuestionGenerationProvider(self._response_client),
            GmsGeneratedQuestionValidator(self._response_client),
            self._execution_policy,
            QuestionGenerationLogContext(
                conversation_id=request.conversation_id,
                turn_id=None,
                response_id=None,
                provider_name=type(self._response_client).__name__,
            ),
            self._logger,
            fallback_text=fallback_question,
        )
        if generated.question is None:
            assistant_question = (
                redirect_context.previous_question
                if redirect_context is not None
                else fallback_question
            )
            assistant_message = _fallback_assistant_message(
                assistant_question,
                redirect_context,
                request.child_answer,
            )
            generation_status = QuestionGenerationStatus.FALLBACK_USED
            generation_source = QuestionGenerationSource.RULE_BASED_FALLBACK
            generation_failure_code = generated.failure_code
        elif generated.source is QuestionGenerationSource.LLM:
            assistant_message = generated.question
            assistant_question = _extract_final_question(generated.question)
            generation_status = generated.status
            generation_source = generated.source
            generation_failure_code = generated.failure_code
        else:
            assistant_question = generated.question
            assistant_message = _fallback_assistant_message(
                assistant_question,
                redirect_context,
                request.child_answer,
            )
            generation_status = generated.status
            generation_source = generated.source
            generation_failure_code = generated.failure_code
        return DialogueTurnResponse(
            updated_slots=updated_slots,
            decision=DialogueTurnDecision.CONTINUE_TOPIC,
            assistant_message=assistant_message,
            assistant_question=assistant_question,
            assistant_target=target,
            updated_skipped_targets=updated_skipped_targets,
            question_generation_status=generation_status,
            question_generation_source=generation_source,
            question_generation_failure_code=generation_failure_code,
        )


def fixed_assistant_messages() -> tuple[str, ...]:
    """AI가 말하는 문장 중 **매번 완전히 동일한** 것들 (지연 캐시 대상).

    일반 질문 경로는 포함하지 않는다 — 그 메시지는 현재 주제 이력과 아이 답변을
    반영해 생성되므로 매 턴 달라진다.

    여기 담기는 것:

    - 무응답 복구 프롬프트(단순화·되묻기·선택지)와 규칙 기반 Fallback 질문. 둘 다
      ``SIMPLIFIED_QUESTION_BY_TARGET``을 공유한다.
    - 낮은 STT 신뢰도 되묻기(``ASK_TO_REPEAT_PROMPT``).
    - 주제 전환 메시지. 전환 질문을 문장에 품고 있어 조합이 유한하다.
    """

    messages: list[str] = [
        *SIMPLIFIED_QUESTION_BY_TARGET.values(),
        *CHOICE_QUESTION_BY_TARGET.values(),
        ASK_TO_REPEAT_PROMPT,
        # 연결 직후 오프닝 질문(DAILY 오프닝은 EVENT 단순화 질문과 동일해 위에 포함).
        *(conversation_opening_question(topic) for topic in DialogueTopic),
        CONVERSATION_CLOSING_MESSAGE,
    ]
    for current in DialogueTopic:
        for following in DialogueTopic:
            if current is following:
                continue
            messages.append(
                _topic_transition_message(
                    current,
                    following,
                    _topic_opening_question(following),
                )
            )
    return tuple(dict.fromkeys(messages))  # 순서 유지 + 중복 제거


def _current_unanswered_target(
    request: DialogueTurnRequest,
) -> QuestionTarget | None:
    if request.current_question is None:
        return None
    if request.current_target is not None:
        return request.current_target
    # 직전에 물은 목표를 추정한다. 건너뛴 목표를 제외하지 않으면 skip 이후의
    # 실제 질문 목표와 어긋나 답변이 엉뚱한 슬롯에 backfill된다.
    return select_next_question_target(
        request.current_slots,
        excluded_targets=request.skipped_targets,
    )


def _resolve_unanswered_target(
    *,
    asked_target: QuestionTarget | None,
    answered_current_question: bool | None,
    updated_slots: DiarySlots,
) -> QuestionTarget | None:
    if asked_target is None or answered_current_question is True:
        return None
    if answered_current_question is False:
        return asked_target
    field_name = QUESTION_TARGET_SLOT_FIELDS[asked_target]
    updated_value = getattr(updated_slots, field_name)
    if updated_value is None or not updated_value.strip():
        return asked_target
    return None


def _backfill_contextual_emotion_reason(
    *,
    slots: DiarySlots,
    request: DialogueTurnRequest,
    asked_target: QuestionTarget | None,
    relevance_decision: AnswerRelevanceDecision | None,
) -> DiarySlots:
    if (
        asked_target is not QuestionTarget.EMOTION
        or _is_filled_slot(slots.emotion_reason)
        or not _is_filled_slot(slots.emotion)
        or not _is_filled_slot(slots.event)
    ):
        return slots
    if (
        relevance_decision is not None
        and relevance_decision.answered is False
    ):
        return slots
    if not _emotion_question_refers_to_existing_event(
        request.current_question
    ):
        return slots
    return slots.model_copy(
        update={"emotion_reason": slots.event.strip()},
        deep=True,
    )


def _emotion_question_refers_to_existing_event(
    current_question: str | None,
) -> bool:
    if current_question is None:
        return False
    normalized = current_question.strip()
    return any(
        cue in normalized
        for cue in ("그때", "그 순간", "그곳", "거기", "할 때")
    )


def _backfill_answered_target(
    *,
    slots: DiarySlots,
    target: QuestionTarget | None,
    child_answer: str,
) -> DiarySlots:
    if target is None:
        return slots
    field_name = QUESTION_TARGET_SLOT_FIELDS[target]
    current_value = getattr(slots, field_name)
    if current_value is not None and current_value.strip():
        return slots
    return slots.model_copy(
        update={field_name: child_answer.strip()},
        deep=True,
    )


def _normalize_first_turn_slots(
    slots: DiarySlots,
    child_answer: str,
    *,
    previous_slots: DiarySlots,
) -> DiarySlots:
    # 첫 턴 추출이 미래 목표 슬롯(emotionReason·memorableMoment)으로 오분류한 값은
    # 버리되, 요청에 이미 있던 기존 값은 지우지 않는다. 필드를 빠뜨리고 재구성하면
    # 채워져 있던 슬롯이 None으로 리셋되어 하류(DB 동기화)까지 지워진다.
    event = slots.event
    if event is None or not event.strip():
        event = child_answer.strip()
    return slots.model_copy(
        update={
            "event": event,
            "emotion_reason": previous_slots.emotion_reason,
            "memorable_moment": previous_slots.memorable_moment,
        },
        deep=True,
    )


def _recoverable_target_for_slot_analysis(
    request: DialogueTurnRequest,
    error: SlotAnalysisError,
) -> QuestionTarget | None:
    if error.code is SlotAnalysisFailureCode.PROVIDER_PERMANENT_FAILURE:
        return None
    if request.current_question is None:
        return QuestionTarget.EVENT
    target = request.current_target
    if target is None:
        return None
    field_name = QUESTION_TARGET_SLOT_FIELDS[target]
    if _slot_has_value(getattr(request.current_slots, field_name)):
        return None
    return target


def _slot_has_value(value: str | None) -> bool:
    return value is not None and bool(value.strip())


def _is_child_question(
    request: DialogueTurnRequest,
    decision: AnswerRelevanceDecision | None,
) -> bool:
    if _looks_like_korean_child_question(request.child_answer):
        return True
    if decision is not None and decision.child_question:
        return True
    return _looks_like_child_question(request.child_answer)


def _looks_like_korean_child_question(child_answer: str) -> bool:
    normalized = " ".join(child_answer.strip().split())
    compact = "".join(normalized.casefold().split())
    if "?" in normalized:
        return True
    direct_question_cues = (
        "너도",
        "너는",
        "선생님은",
        "ai는",
        "에이아이는",
        "뭐라고",
        "어떻게",
        "왜",
    )
    question_cues = (
        "누구",
        "뭐",
        "무엇",
        "어떤",
        "어디",
        "언제",
        "왜",
        "어떻게",
        "얼마",
        "좋아",
        "먹어",
        "먹을",
        "할수",
        "할줄",
        "가봤",
        "봤어",
        "해야",
        "말해",
    )
    return (
        any(cue in compact for cue in direct_question_cues)
        and any(cue in compact for cue in question_cues)
    )


def _looks_like_child_question(child_answer: str) -> bool:
    normalized = " ".join(child_answer.strip().split())
    if "?" in normalized or "？" in normalized:
        return True
    ai_directed_prefixes = (
        "너는 ",
        "넌 ",
        "너도 ",
        "선생님은 ",
        "ai는 ",
        "인공지능은 ",
    )
    if not normalized.casefold().startswith(ai_directed_prefixes):
        return False
    question_cues = (
        "누구",
        "뭐",
        "무슨",
        "어떤",
        "어디",
        "언제",
        "왜",
        "어떻게",
        "얼마",
        "좋아해",
        "싫어해",
        "알아",
        "생각해",
        "어때",
    )
    return any(cue in normalized.casefold() for cue in question_cues)


def _build_redirect_context(
    *,
    request: DialogueTurnRequest,
    target: QuestionTarget,
    unanswered_target: QuestionTarget | None,
    relevance_decision: AnswerRelevanceDecision | None,
) -> QuestionRedirectContext | None:
    if (
        relevance_decision is None
        or relevance_decision.answered
        or request.current_question is None
    ):
        return None
    return QuestionRedirectContext(
        previous_question=request.current_question,
        child_answer=request.child_answer,
        child_question=_is_child_question(request, relevance_decision),
        bridge_hint=(
            None
            if relevance_decision is None
            else relevance_decision.bridge_response
        ),
    )


def _available_question_targets(
    slots: DiarySlots,
    skipped_targets: frozenset[QuestionTarget],
) -> tuple[QuestionTarget, ...]:
    return tuple(
        target
        for target, field_name in QUESTION_TARGET_SLOT_FIELDS.items()
        if target not in skipped_targets
        and not _is_filled_slot(getattr(slots, field_name))
    )


def _select_contextual_target(
    *,
    suggested_target: QuestionTarget | None,
    fallback_target: QuestionTarget | None,
    slots: DiarySlots,
    skipped_targets: frozenset[QuestionTarget],
) -> QuestionTarget | None:
    excluded_targets = skipped_targets
    if suggested_target is not None:
        field_name = QUESTION_TARGET_SLOT_FIELDS[suggested_target]
        if (
            suggested_target not in excluded_targets
            and not _is_filled_slot(getattr(slots, field_name))
        ):
            return suggested_target
    if fallback_target in excluded_targets:
        return select_next_question_target(
            slots,
            excluded_targets=excluded_targets,
        )
    return fallback_target


def _should_offer_optional_final_prompt(
    *,
    request: DialogueTurnRequest,
    slots: DiarySlots,
    reason: DialogueTerminationReason | None,
) -> bool:
    if reason is not DialogueTerminationReason.REQUIRED_SLOTS_FILLED:
        return False
    if request.question_count >= request.max_question_count:
        return False
    if request.question_count <= 3:
        return not _has_asked_optional_final_question(request)
    if request.current_target is QuestionTarget.MEMORABLE_MOMENT:
        return False
    if _is_filled_slot(slots.memorable_moment):
        return False
    return not _has_asked_optional_final_question(request)


def _has_asked_optional_final_question(
    request: DialogueTurnRequest,
) -> bool:
    return any(
        _is_optional_final_question(question)
        for question in (*request.recent_questions, request.current_question)
        if question is not None
    )


def _is_optional_final_question(question: str) -> bool:
    normalized = "".join(question.strip().split())
    return normalized == "".join(optional_final_question().split())


def _closing_acknowledgement(child_answer: str) -> str:
    return acknowledge_child_answer(child_answer)


def _is_filled_slot(value: str | None) -> bool:
    return value is not None and bool(value.strip())


def _fallback_assistant_message(
    question: str,
    redirect_context: QuestionRedirectContext | None,
    child_answer: str | None,
) -> str:
    if redirect_context is None:
        return build_fallback_assistant_message(
            child_answer=child_answer,
            question=question,
        )
    return build_fallback_assistant_message(
        child_answer=child_answer,
        question=question,
        bridge_hint=redirect_context.bridge_hint,
        child_question=redirect_context.child_question,
    )


def _extract_final_question(message: str) -> str:
    """Return the final question clause from one validated generated turn."""

    normalized = message.strip()
    markers = (". ", "! ", "。", "！")
    marker, boundary = max(
        ((marker, normalized.rfind(marker)) for marker in markers),
        key=lambda item: item[1],
    )
    return (
        normalized[boundary + len(marker) :].strip()
        if boundary >= 0
        else normalized
    )


def _topic_fallback_question(
    topic: DialogueTopic,
    target: QuestionTarget,
    context: GameContext | DialogueTopicContext | None,
) -> str:
    if topic is not DialogueTopic.GAME:
        return fallback_question_for_target(target)
    if target is QuestionTarget.EVENT:
        game_name = _single_game_name(context)
        if game_name is not None:
            return f"{game_name}에서 어떤 일이 있었어?"
        return "게임에서 어떤 일이 있었어?"
    if target is QuestionTarget.EMOTION:
        return "게임할 때 어떤 기분이었어?"
    if target is QuestionTarget.EMOTION_REASON:
        return "어떤 점 때문에 그런 기분이 들었어?"
    return optional_final_question()


def _single_game_name(
    context: GameContext | DialogueTopicContext | None,
) -> str | None:
    if not isinstance(context, DialogueTopicContext):
        return None
    names = [
        game.game_name.strip()
        for game in context.games
        if game.game_name and game.game_name.strip()
    ]
    return names[0] if len(names) == 1 else None


_GAME_OPENING_QUESTION = "오늘 했던 게임 중 가장 기억나는 게임은 무엇이었어?"

# 대화가 끝날 때 마지막으로 들려주는 고정 인사. 종료 응답 자체는 계약상 메시지를
# 담지 않으므로(터미널 응답 필드 검증) 세션 계층이 이 문장을 따로 발화한다.
CONVERSATION_CLOSING_MESSAGE = "오늘 이야기 들려줘서 고마워! 다음에 또 이야기하자."


def conversation_opening_question(
    topic: DialogueTopic,
    game_names: Sequence[str] = (),
) -> str:
    """연결 직후 AI가 먼저 던지는 주제별 고정 첫 질문.

    주제 전환용 ``_topic_opening_question``과 달리 대화의 맨 처음에 쓰므로
    "이번에는/다른 일도" 같은 전환 표현이 없어야 한다. DAILY는 이미 검증된
    EVENT 단순화 질문을 재사용한다.

    오늘 한 게임이 하나뿐이면 그 이름을 불러 대화를 연다. 여러 개일 때는 임의로
    하나를 고르지 않고 "가장 기억나는 게임"을 묻는 기존 질문이 더 자연스럽다.
    """

    if topic is not DialogueTopic.GAME:
        return SIMPLIFIED_QUESTION_BY_TARGET[QuestionTarget.EVENT]
    named = [name.strip() for name in game_names if name and name.strip()]
    if len(named) == 1:
        return f"오늘 {named[0]} 했구나! 어떤 순간이 제일 재미있었어?"
    return _GAME_OPENING_QUESTION


def conversation_opening_target(
    topic: DialogueTopic,
    game_names: Sequence[str] = (),
) -> QuestionTarget:
    """Return the slot meaning that exactly matches the opening wording."""

    named = [name.strip() for name in game_names if name and name.strip()]
    if topic is DialogueTopic.GAME and len(named) == 1:
        return QuestionTarget.MEMORABLE_MOMENT
    return QuestionTarget.EVENT


def build_dialogue_opening(
    request: DialogueOpeningRequest,
) -> DialogueOpeningResponse:
    """Return the same first AI turn used by the realtime dialogue manager."""

    game_names = [
        game.game_name
        for game in request.topic_context.games
        if game.game_name
    ]
    question = conversation_opening_question(
        request.current_topic,
        game_names,
    )
    return DialogueOpeningResponse(
        assistantMessage=question,
        assistantQuestion=question,
        assistantTarget=conversation_opening_target(
            request.current_topic,
            game_names,
        ),
    )


def _topic_opening_question(topic: DialogueTopic) -> str:
    if topic is DialogueTopic.DAILY:
        return "이번에는 오늘 있었던 다른 일도 이야기해 줄래?"
    return _GAME_OPENING_QUESTION


def _topic_transition_message(
    current_topic: DialogueTopic,
    next_topic: DialogueTopic,
    question: str,
) -> str:
    if (
        current_topic is DialogueTopic.GAME
        and next_topic is DialogueTopic.DAILY
    ):
        return f"게임 이야기를 들려줘서 고마워! {question}"
    return f"이야기를 들려줘서 고마워! {question}"
