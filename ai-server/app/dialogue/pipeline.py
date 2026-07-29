"""Stateless orchestration for one text-based diary dialogue turn."""

import asyncio
import logging
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
)
from app.dialogue.analysis.answer_relevance import (
    AnswerRelevanceDecision,
    AnswerRelevanceLogContext,
    AnswerRelevanceRequest,
    classify_answer_relevance_with_retry,
)
from app.dialogue.question.child_reply import (
    ChildQuestionReplyLogContext,
    ChildQuestionReplyRequest,
    generate_child_question_reply_with_retry,
)
from app.dialogue.question.fallback import (
    QuestionGenerationExecutionPolicy,
    QuestionGenerationFailureCode,
    QuestionGenerationSource,
    QuestionGenerationStatus,
    build_fallback_question,
)
from app.dialogue.question.prompts import (
    QuestionRedirectContext,
)
from app.dialogue.policy.recovery import (
    ASK_TO_REPEAT_PROMPT,
    CHOICE_QUESTION_BY_TARGET,
    DEFAULT_NO_RESPONSE_RECOVERY_POLICY,
    SIMPLIFIED_QUESTION_BY_TARGET,
    evaluate_dialogue_policy,
)
from app.dialogue.schemas import (
    DialogueTopic,
    DialogueTopicContext,
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
from app.dialogue.policy.termination import (
    ChildTurnSignal,
    DialoguePolicyRequest,
    DialogueTerminationPolicy,
    DialogueTerminationReason,
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
        asked_target = _current_unanswered_target(request)
        slot_analysis = analyze_answer_with_retry(
            DiaryAnswerAnalysisRequest(
                current_question=request.current_question,
                child_answer=request.child_answer,
                stt_confidence=request.stt_confidence,
                current_slots=request.current_slots,
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
            if _can_recover_first_turn_slot_analysis(request, error):
                updated_slots = request.current_slots.model_copy(
                    update={"event": request.child_answer},
                    deep=True,
                )
            else:
                raise DialoguePipelineError(
                    DialoguePipelineFailureCode.SLOT_ANALYSIS_FAILED
                ) from None

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
        else:
            updated_slots = enrich_explicit_people(
                updated_slots,
                request.child_answer,
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
                skipped_targets=request.skipped_targets,
            ),
            self._termination_policy,
            DEFAULT_NO_RESPONSE_RECOVERY_POLICY,
        )
        if policy_decision.should_terminate:
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
            )

        target = policy_decision.next_target
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
        if (
            redirect_context is not None
            and redirect_context.child_question
        ):
            direct_reply = await generate_child_question_reply_with_retry(
                ChildQuestionReplyRequest(
                    child_question=request.child_answer,
                ),
                self._response_client,
                self._execution_policy,
                ChildQuestionReplyLogContext(
                    conversation_id=request.conversation_id,
                    request_id=request.request_id,
                ),
                self._logger,
            )
            used_fallback = direct_reply is None
            if direct_reply is None:
                direct_reply = _safe_child_question_reply(
                    redirect_context.bridge_hint
                )
            return DialogueTurnResponse(
                updated_slots=updated_slots,
                decision=DialogueTurnDecision.CONTINUE_TOPIC,
                assistant_message=_join_child_reply_and_question(
                    direct_reply,
                    redirect_context.previous_question,
                ),
                assistant_question=redirect_context.previous_question,
                question_generation_status=(
                    QuestionGenerationStatus.FALLBACK_USED
                    if used_fallback
                    else QuestionGenerationStatus.GENERATED
                ),
                question_generation_source=(
                    QuestionGenerationSource.RULE_BASED_FALLBACK
                    if used_fallback
                    else QuestionGenerationSource.LLM
                ),
                question_generation_failure_code=(
                    QuestionGenerationFailureCode.GENERATED_QUESTION_REJECTED
                    if used_fallback
                    else None
                ),
            )
        if redirect_context is not None:
            used_fallback = redirect_context.bridge_hint is None
            bridge = (
                redirect_context.bridge_hint
                or "알려 줘서 고마워."
            )
            return DialogueTurnResponse(
                updated_slots=updated_slots,
                decision=DialogueTurnDecision.CONTINUE_TOPIC,
                assistant_message=_join_bridge_and_question(
                    bridge,
                    redirect_context.previous_question,
                ),
                assistant_question=redirect_context.previous_question,
                question_generation_status=(
                    QuestionGenerationStatus.FALLBACK_USED
                    if used_fallback
                    else QuestionGenerationStatus.GENERATED
                ),
                question_generation_source=(
                    QuestionGenerationSource.RULE_BASED_FALLBACK
                    if used_fallback
                    else QuestionGenerationSource.LLM
                ),
                question_generation_failure_code=(
                    QuestionGenerationFailureCode.UNEXPECTED_PROVIDER_FAILURE
                    if used_fallback
                    else None
                ),
            )
        assistant_question = build_fallback_question(target).text
        return DialogueTurnResponse(
            updated_slots=updated_slots,
            decision=DialogueTurnDecision.CONTINUE_TOPIC,
            assistant_message=_join_answer_and_question(
                request.child_answer,
                assistant_question,
            ),
            assistant_question=assistant_question,
            question_generation_status=(
                QuestionGenerationStatus.TEMPLATE_USED
            ),
            question_generation_source=(
                QuestionGenerationSource.RULE_BASED_TEMPLATE
            ),
            question_generation_failure_code=None,
        )


def fixed_assistant_messages() -> tuple[str, ...]:
    """AI가 말하는 문장 중 **매번 완전히 동일한** 것들 (사전 합성 대상).

    일반 질문 경로는 포함하지 않는다 — 그 메시지는 아이 답변에서 파생된 호응으로
    시작하므로 매 턴 달라진다(``_join_answer_and_question``).

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


def _can_recover_first_turn_slot_analysis(
    request: DialogueTurnRequest,
    error: SlotAnalysisError,
) -> bool:
    return (
        request.current_question is None
        and error.code
        is not SlotAnalysisFailureCode.PROVIDER_PERMANENT_FAILURE
    )


def _is_child_question(
    request: DialogueTurnRequest,
    decision: AnswerRelevanceDecision | None,
) -> bool:
    if decision is not None and decision.child_question:
        return True
    return _looks_like_child_question(request.child_answer)


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
        unanswered_target is None
        or target != unanswered_target
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


def _join_child_reply_and_question(
    direct_reply: str,
    previous_question: str,
) -> str:
    reply = direct_reply.rstrip()
    if not reply.endswith((".", "!")):
        reply = f"{reply}!"
    return (
        f"{reply} 아까 이야기로 돌아가서, "
        f"{previous_question}"
    )


def _join_bridge_and_question(
    bridge: str,
    previous_question: str,
) -> str:
    acknowledgement = bridge.rstrip()
    if acknowledgement.endswith("."):
        acknowledgement = f"{acknowledgement[:-1]}!"
    elif not acknowledgement.endswith("!"):
        acknowledgement = f"{acknowledgement}!"
    return f"{acknowledgement} {previous_question}"


def _join_answer_and_question(
    child_answer: str,
    question: str,
) -> str:
    acknowledgement = _to_acknowledgement(child_answer)
    return f"{acknowledgement} {question}"


def _to_acknowledgement(child_answer: str) -> str:
    statement = child_answer.strip().rstrip(".!?。！？")
    polite_suffixes = (
        ("해요", "하구나"),
        ("어요", "구나"),
        ("아요", "구나"),
    )
    casual_suffixes = (
        ("해", "하구나"),
        ("어", "구나"),
        ("아", "구나"),
        ("야", "구나"),
    )
    for suffix, replacement in (*polite_suffixes, *casual_suffixes):
        if statement.endswith(suffix) and len(statement) > len(suffix):
            return f"{statement[:-len(suffix)]}{replacement}."
    return f"{statement}구나."


_GAME_OPENING_QUESTION = "오늘 했던 게임 중 가장 기억나는 게임은 무엇이었어?"


def conversation_opening_question(topic: DialogueTopic) -> str:
    """연결 직후 AI가 먼저 던지는 주제별 고정 첫 질문.

    주제 전환용 ``_topic_opening_question``과 달리 대화의 맨 처음에 쓰므로
    "이번에는/다른 일도" 같은 전환 표현이 없어야 한다. DAILY는 이미 검증된
    EVENT 단순화 질문을 재사용한다.
    """

    if topic is DialogueTopic.GAME:
        return _GAME_OPENING_QUESTION
    return SIMPLIFIED_QUESTION_BY_TARGET[QuestionTarget.EVENT]


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


def _safe_child_question_reply(bridge_hint: str | None) -> str:
    if (
        bridge_hint is not None
        and bridge_hint.startswith("나는")
        and "?" not in bridge_hint
        and len(bridge_hint) <= 40
    ):
        return bridge_hint
    return "나는 그 질문에는 딱 하나를 고르기 어려워!"
