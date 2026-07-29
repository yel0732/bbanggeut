"""Realtime dialogue orchestration for one connection (DIARY-3 T11~T15).

Connects the W02 turn boundary to the dialogue policy DIARY-1 already
validated. This module deliberately contains **no dialogue policy of its own**
(W03 plan §2-1): slot analysis, target selection, question generation with
validator/fallback, termination and the no-response ladder all run inside the
reused ``DialogueTurnPipeline`` / ``evaluate_dialogue_policy``. What is new
here is exactly the part Spring used to do between text turns —

- keeping per-session state the batch pipeline is stateless about
  (``DialogueSessionState``: topic progression, slots, question counts,
  recent questions),
- issuing realtime identifiers (AI turns share the child turns'
  ``TurnSequencer`` — one ``turnSeq`` sequence per conversation, §4),
- the cancellation/epoch boundary (ADR-012): an LLM result is a
  non-cancellable Provider outcome, so it is awaited and then **discarded**
  right before publishing if its ``responseId`` was cancelled or the
  connection's epoch is no longer current,
- publishing ``assistant.response.created`` / ``assistant.text.final``.

Nothing here reaches Redis Streams (W05) or TTS (W04).
"""

from __future__ import annotations

import logging
import uuid
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from app.dialogue.pipeline import (
    CONVERSATION_CLOSING_MESSAGE,
    DialoguePipelineError,
    DialogueTurnDecision,
    DialogueTurnPipeline,
    DialogueTurnRequest,
    DialogueTurnResponse,
    conversation_opening_question,
)
from app.dialogue.policy.recovery import (
    ASK_TO_REPEAT_PROMPT,
    DEFAULT_NO_RESPONSE_RECOVERY_POLICY,
    evaluate_dialogue_policy,
)
from app.dialogue.policy.target import QuestionTarget, select_next_question_target
from app.dialogue.policy.termination import (
    ChildTurnSignal,
    DialoguePolicyRequest,
    DialogueTerminationPolicy,
)
from app.dialogue.question.fallback import build_fallback_question
from app.dialogue.schemas import DialogueTopic, DialogueTopicContext, DiarySlots
from app.events.outbound import ConversationEventPublisher
from app.events.stream import ConversationStreamProducer
from app.realtime.context_snapshot import ContextSnapshot, TopicSnapshot
from app.session.response_registry import ResponseRegistry
from app.speech.recognizer import SttResult
from app.speech.speaker import SpeakerHandle
from app.speech.stt_pipeline import SessionSttPipeline
from app.speech.synthesis import SpeechSynthesisService
from app.speech.turn_state import SttTurn, TurnSequencer

RequestIdFactory = Callable[[], str]
EpochCurrentCheck = Callable[[], bool]
# (turn_id, turn_seq, response_id, text) — 발행된 AI 질문을 음성으로 합성한다.
SpeakCallback = Callable[[str, int, str, str], None]

_RECENT_QUESTION_LIMIT = 5
_LLM_ERROR_CODE = "LLM_TIMEOUT"  # §22의 LLM 계열 공개 코드(복구: Fallback)
_STT_LOW_CONFIDENCE_CODE = "STT_LOW_CONFIDENCE"  # §22 — 복구: 확인 질문


def _default_request_id_factory() -> str:
    return f"req_{uuid.uuid4().hex}"


@dataclass(frozen=True)
class _AiTurnRef:
    """Identifiers of a published AI turn, threaded to the Stream event."""

    turn_id: str
    turn_seq: int
    response_id: str


@dataclass
class _TopicState:
    """Mutable per-topic progress the snapshot deliberately does not carry (§8)."""

    snapshot: TopicSnapshot
    slots: DiarySlots
    question_count: int = 0
    skipped_targets: frozenset[QuestionTarget] = field(default_factory=frozenset)


class DialogueSessionState:
    """One connection's dialogue memory, seeded from the Context Snapshot."""

    def __init__(self, snapshot: ContextSnapshot) -> None:
        topics = snapshot.ordered_topics()
        if not topics:
            raise ValueError("context snapshot carries no topics")
        self.snapshot = snapshot
        self._topics = [
            _TopicState(
                snapshot=topic,
                slots=topic.initial_slots.model_copy(deep=True),
                question_count=topic.question_count,
            )
            for topic in topics
        ]
        # 재연결 시 스냅숏의 currentTopic이 뒤 주제일 수 있다 — 그 주제부터 시작.
        self._topic_index = next(
            (
                index
                for index, topic in enumerate(self._topics)
                if topic.snapshot.type is snapshot.conversation.current_topic
            ),
            0,
        )
        self.recent_questions: list[str] = []
        self.last_question: str | None = None
        self.consecutive_no_responses = 0
        self.consecutive_low_confidence_reasks = 0
        self.terminated = False
        self.known_turn_ids: set[str] = set()

    @property
    def current_topic(self) -> _TopicState:
        return self._topics[self._topic_index]

    @property
    def next_topic_type(self) -> DialogueTopic | None:
        if self._topic_index + 1 < len(self._topics):
            return self._topics[self._topic_index + 1].snapshot.type
        return None

    @property
    def total_question_count(self) -> int:
        return sum(topic.question_count for topic in self._topics)

    def advance_topic(self) -> None:
        if self._topic_index + 1 >= len(self._topics):
            raise ValueError("no next topic to advance to")
        self._topic_index += 1

    def remember_question(self, question: str) -> None:
        self.recent_questions.append(question)
        del self.recent_questions[:-_RECENT_QUESTION_LIMIT]
        self.last_question = question


class RealtimeDialogueManager:
    """Drives one conversation: child turns in, AI question events out."""

    def __init__(
        self,
        *,
        dialogue_pipeline: DialogueTurnPipeline,
        termination_policy: DialogueTerminationPolicy,
        publisher: ConversationEventPublisher,
        state: DialogueSessionState,
        turn_sequencer: TurnSequencer,
        response_registry: ResponseRegistry,
        is_epoch_current: EpochCurrentCheck,
        logger: logging.Logger,
        request_id_factory: RequestIdFactory = _default_request_id_factory,
        speak: SpeakCallback | None = None,
        stream_producer: ConversationStreamProducer | None = None,
        clock: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
        low_confidence_threshold: float = 0.45,
        low_confidence_reask_limit: int = 1,
    ) -> None:
        self._pipeline = dialogue_pipeline
        self._termination_policy = termination_policy
        self._publisher = publisher
        self._state = state
        self._turns = turn_sequencer
        self._responses = response_registry
        self._is_epoch_current = is_epoch_current
        self._logger = logger
        self._request_id_factory = request_id_factory
        self._speak = speak
        self._stream = stream_producer
        self._clock = clock
        self._low_confidence_threshold = low_confidence_threshold
        self._low_confidence_reask_limit = low_confidence_reask_limit

    def set_speak(self, speak: SpeakCallback) -> None:
        """Wire the synthesis speak callback after construction.

        Assembly builds the manager first (synthesis needs the manager's
        ``emit_ai_turn_completed``), then sets this — resolving the circular
        speak↔on_completed dependency.
        """

        self._speak = speak

    # --- guard hooks (T13) -------------------------------------------------

    def is_response_cancelled(self, response_id: str) -> bool:
        return self._responses.is_cancelled(response_id)

    def is_known_turn(self, turn_id: str) -> bool:
        return turn_id in self._state.known_turn_ids

    # --- child turn (T12/T14/T15) -------------------------------------------

    async def handle_child_turn(self, turn: SttTurn, result: SttResult) -> None:
        """``SessionSttPipeline.on_final`` — one confirmed child turn in."""

        state = self._state
        if state.terminated:
            self._logger.info(
                "child turn ignored: conversation already terminated",
                extra={"turn_id": turn.turn_id},
            )
            return
        state.consecutive_no_responses = 0
        state.known_turn_ids.add(turn.turn_id)

        if self._should_reask_low_confidence(result):
            self._reask_low_confidence(turn)
            return
        state.consecutive_low_confidence_reasks = 0
        # 확정 발행은 신뢰도 게이트 뒤에서만 한다 — 슬롯 분석에도 쓰지 않기로 한
        # 저신뢰 전사가 CHILD_TURN_FINALIZED로 남으면 대화 원문(MySQL)이 오인식
        # 텍스트로 오염된다. 발행되는 턴 = 실제로 대화에 쓰인 턴(§12 확정만 발행).
        self._emit_child_turn_finalized(turn, result)

        topic = state.current_topic
        request = DialogueTurnRequest(
            request_id=self._request_id_factory(),
            conversation_id=state.snapshot.conversation_id,
            child_answer=result.text,
            stt_confidence=result.confidence,
            current_question=state.last_question,
            current_topic=topic.snapshot.type,
            next_topic=state.next_topic_type,
            current_slots=topic.slots,
            recent_questions=tuple(state.recent_questions),
            game_context=DialogueTopicContext(
                topic=topic.snapshot.type,
                games=topic.snapshot.games,
            ),
            question_count=min(topic.question_count, topic.snapshot.max_question_count),
            max_question_count=topic.snapshot.max_question_count,
            skipped_targets=topic.skipped_targets,
        )
        try:
            response = await self._pipeline.process(request)
        except DialoguePipelineError as error:
            self._logger.warning(
                "dialogue pipeline failed",
                extra={"turn_id": turn.turn_id, "failure_code": error.code.value},
            )
            self._publisher.publish(
                "error",
                {
                    "code": _LLM_ERROR_CODE,
                    "message": "질문 생성에 실패했습니다.",
                    "recoverable": True,
                },
                turn_id=turn.turn_id,
                turn_seq=turn.turn_seq,
            )
            return

        self._apply_turn_response(response)

    def _apply_turn_response(self, response: DialogueTurnResponse) -> None:
        state = self._state
        topic = state.current_topic

        if response.decision is DialogueTurnDecision.TERMINATE_SESSION:
            # DIARY-1 계약상 terminal 응답에는 메시지가 없다 — 마무리 멘트는
            # 캐릭터 톤 제품 결정과 W04/W05에서 재론(W03 plan §7-G).
            topic.slots = response.updated_slots
            state.terminated = True
            self._logger.info(
                "conversation terminated by dialogue policy",
                extra={"termination_reason": _reason_value(response)},
            )
            self._emit_session_completed(_reason_value(response))
            return

        ai_ref = self._publish_assistant_text(response.assistant_message)
        if ai_ref is None:
            return

        if response.decision is DialogueTurnDecision.SWITCH_TOPIC:
            topic.slots = response.updated_slots  # 끝난 주제의 최종 슬롯
            # 전환 질문은 목표가 없다 — 새 주제 오프닝이다(§11-C: null target).
            state.advance_topic()
            # 전환 질문은 새 주제의 첫 질문으로 센다.
            state.current_topic.question_count = 1
            self._emit_ai_turn_generated(
                ai_ref,
                response.assistant_message,
                response.assistant_question,
                None,
                state.current_topic,
                slots_topic=topic,
                topic_completion_reason=_reason_value(response),
            )
        else:
            topic.slots = response.updated_slots
            topic.question_count += 1
            # 이 질문이 겨냥한 목표를 갱신 슬롯으로 재계산한다(§11-C, 결정론적).
            target = select_next_question_target(topic.slots)
            self._emit_ai_turn_generated(
                ai_ref,
                response.assistant_message,
                response.assistant_question,
                target,
                topic,
            )
        state.remember_question(response.assistant_question)

    # --- 낮은 STT 신뢰도 되묻기 (§22 STT_LOW_CONFIDENCE) ----------------------

    def _should_reask_low_confidence(self, result: SttResult) -> bool:
        """되묻기 게이트: 신뢰도가 낮은 Final을 대화에 넣기 전에 확인 질문으로 돌린다.

        오인식된 전사가 슬롯·일기로 흘러가는 것을 막는 유일한 회복 수단이다 —
        아이의 재발화가 추측 없이 원문을 복원한다. 연속 상한에 도달하면 낮은
        신뢰도라도 진행한다(무한 되묻기 방지). 그때는 ``sttConfidence``를 함께
        받은 슬롯 분석 LLM이 보수적으로 해석한다.
        """

        confidence = result.confidence
        return (
            confidence is not None
            and confidence < self._low_confidence_threshold
            and self._state.consecutive_low_confidence_reasks
            < self._low_confidence_reask_limit
        )

    def _reask_low_confidence(self, turn: SttTurn) -> None:
        state = self._state
        state.consecutive_low_confidence_reasks += 1
        self._logger.info(
            "child turn re-asked: low stt confidence",
            extra={
                "turn_id": turn.turn_id,
                "consecutive_reasks": state.consecutive_low_confidence_reasks,
            },
        )
        self._publisher.publish(
            "error",
            {
                "code": _STT_LOW_CONFIDENCE_CODE,
                "message": "음성 인식 신뢰도가 낮습니다.",
                "recoverable": True,
            },
            turn_id=turn.turn_id,
            turn_seq=turn.turn_seq,
        )
        topic = state.current_topic
        ai_ref = self._publish_assistant_text(ASK_TO_REPEAT_PROMPT)
        if ai_ref is not None:
            target = select_next_question_target(
                topic.slots, excluded_targets=topic.skipped_targets
            )
            self._emit_ai_turn_generated(
                ai_ref, ASK_TO_REPEAT_PROMPT, ASK_TO_REPEAT_PROMPT, target, topic
            )
        # last_question은 갱신하지 않는다 — 재발화는 원래 질문에 대한 답으로
        # 평가되어야 한다. 질문 수도 세지 않는다(무응답 재질문 §7-E와 동일).

    # --- 대화 오프닝 ----------------------------------------------------------

    def open_conversation(self) -> None:
        """연결 직후 AI가 첫 질문을 먼저 던진다 — 아이가 먼저 말할 필요가 없다.

        주제 전환 오프닝과 동일하게 첫 질문으로 세며(question_count=1),
        last_question을 세팅해 아이의 첫 답변이 이 질문에 대한 답으로 평가되게
        한다. 이미 질문이 나갔거나 진행된 세션(스냅숏 복원)에는 다시 열지 않는다.
        """

        state = self._state
        topic = state.current_topic
        if (
            state.terminated
            or state.last_question is not None
            or topic.question_count > 0
        ):
            return
        question = conversation_opening_question(
            topic.snapshot.type,
            [game.game_name for game in topic.snapshot.games if game.game_name],
        )
        ai_ref = self._publish_assistant_text(question)
        if ai_ref is None:
            return
        topic.question_count = 1
        target = select_next_question_target(topic.slots)
        self._emit_ai_turn_generated(ai_ref, question, question, target, topic)
        state.remember_question(question)

    # --- no-response (ADR-018 연결) -----------------------------------------

    def handle_no_speech(self) -> None:
        """``SessionSttPipeline.on_no_speech`` — W02가 감지, 정책은 DIARY-1 재사용."""

        state = self._state
        if state.terminated:
            return
        topic = state.current_topic
        current_target = select_next_question_target(
            topic.slots, excluded_targets=topic.skipped_targets
        )
        if current_target is None:
            # 물을 목표가 남지 않았는데 답도 없다 — 종료가 유일한 결정이다.
            # 종료 이벤트를 발행하지 않으면 Spring 세션이 IN_PROGRESS로 방치되어
            # reconciler가 lease 만료를 감지할 때까지 유령 세션이 남는다.
            state.terminated = True
            self._logger.info("conversation terminated: no target left on no-response")
            all_filled = select_next_question_target(topic.slots) is None
            self._emit_session_completed(
                "REQUIRED_SLOTS_FILLED" if all_filled else "INACTIVITY_TIMEOUT"
            )
            return
        decision = evaluate_dialogue_policy(
            DialoguePolicyRequest(
                slots=topic.slots,
                question_count=topic.question_count,
                max_question_count=topic.snapshot.max_question_count,
                turn_signal=ChildTurnSignal.NO_RESPONSE,
                previous_consecutive_no_responses=state.consecutive_no_responses,
                current_target=current_target,
                skipped_targets=topic.skipped_targets,
            ),
            self._termination_policy,
            DEFAULT_NO_RESPONSE_RECOVERY_POLICY,
        )
        state.consecutive_no_responses = decision.consecutive_no_responses
        topic.skipped_targets = decision.skipped_targets
        if decision.should_terminate:
            state.terminated = True
            self._logger.info(
                "conversation terminated by no-response ladder",
                extra={"consecutive_no_responses": decision.consecutive_no_responses},
            )
            self._emit_session_completed("INACTIVITY_TIMEOUT")
            return

        if decision.recovery_prompt is not None:
            prompt_text = decision.recovery_prompt.text
            target = decision.next_target
        else:
            # SKIP: 다음 목표의 규칙 기반 질문으로 넘어간다(ADR-017).
            prompt_text = build_fallback_question(decision.next_target).text
            target = decision.next_target
        ai_ref = self._publish_assistant_text(prompt_text)
        if ai_ref is not None:
            self._emit_ai_turn_generated(
                ai_ref, prompt_text, prompt_text, target, topic
            )
            # 재질문은 질문 수에 세지 않는다(W03 plan §7-E) — 상태만 갱신.
            self._state.remember_question(prompt_text)

    # --- publishing (T15) ----------------------------------------------------

    def _publish_assistant_text(self, text: str) -> _AiTurnRef | None:
        """Issue an AI turn + response and publish it, unless it must be discarded.

        Returns the AI turn's identifiers on success (so the caller can emit the
        matching Stream event), or ``None`` when discarded. The re-check happens
        here, after any await points, because the LLM call cannot be interrupted
        — cancelled/stale results are dropped at the last gate (ADR-012).
        """

        state = self._state
        ai_turn = self._turns.next_turn()
        response_id = self._responses.issue()

        if self._responses.is_cancelled(response_id) or not self._is_epoch_current():
            self._logger.info(
                "assistant response discarded before publish",
                extra={"turn_id": ai_turn.turn_id, "response_id": response_id},
            )
            return None

        state.known_turn_ids.add(ai_turn.turn_id)
        self._publisher.publish(
            "assistant.response.created",
            {},
            turn_id=ai_turn.turn_id,
            turn_seq=ai_turn.turn_seq,
            response_id=response_id,
        )
        self._publisher.publish(
            "assistant.text.final",
            {"text": text},
            turn_id=ai_turn.turn_id,
            turn_seq=ai_turn.turn_seq,
            response_id=response_id,
        )
        if self._speak is not None:
            # 텍스트 발행 직후 음성 합성을 시작한다(W04). 미주입이면 텍스트 전용.
            self._speak(ai_turn.turn_id, ai_turn.turn_seq, response_id, text)
        return _AiTurnRef(ai_turn.turn_id, ai_turn.turn_seq, response_id)

    # --- Stream 발행 (W05) ---------------------------------------------------

    def _emit_child_turn_finalized(self, turn: SttTurn, result: SttResult) -> None:
        if self._stream is None:
            return
        completed_at = self._clock()
        # 발화 시작 시각은 근사한다: SttResult에 wall 시각이 없고 오디오 길이는
        # 정확하므로 오차는 finalize 지연(수십 ms)뿐이다(§11-E).
        started_at = completed_at - timedelta(seconds=result.audio_seconds)
        self._stream.publish_child_turn_finalized(
            turn_id=turn.turn_id,
            turn_seq=turn.turn_seq,
            text=result.text,
            stt_confidence=result.confidence,
            started_at=started_at,
            completed_at=completed_at,
            topic_type=self._state.current_topic.snapshot.type.value,
        )

    def _emit_ai_turn_generated(
        self,
        ref: _AiTurnRef,
        text: str,
        question_text: str,
        target: QuestionTarget | None,
        turn_topic: _TopicState,
        *,
        slots_topic: _TopicState | None = None,
        topic_completion_reason: str | None = None,
    ) -> None:
        if self._stream is None:
            return
        slots_owner = slots_topic or turn_topic
        self._stream.publish_ai_turn_generated(
            turn_id=ref.turn_id,
            turn_seq=ref.turn_seq,
            response_id=ref.response_id,
            text=text,
            question_text=question_text,
            target_slot=target.value if target is not None else None,
            topic_type=turn_topic.snapshot.type.value,
            slots_topic_type=slots_owner.snapshot.type.value,
            topic_slots=slots_owner.slots,
            topic_question_count=turn_topic.question_count,
            session_question_count=self._state.total_question_count,
            topic_completion_reason=topic_completion_reason,
        )

    def _emit_session_completed(self, reason: str | None) -> None:
        """대화를 마무리한다 — 인사 발화 → 종료 신호 → 확정 이벤트 순.

        인사는 DataChannel과 TTS로만 내보내고 Stream에는 싣지 않는다. 질문이 아니고
        슬롯도 바꾸지 않는 정형 문구라 대화 기록에 남길 내용이 아니다.
        ``session.completed``는 프론트가 종료 UI를 띄우고 연결을 정리하는 신호이며,
        인사 오디오가 끝나는 시점은 같은 responseId의 ``assistant.audio.completed``로
        판단할 수 있다.
        """

        resolved = reason or "COMPLETED"
        self._publish_assistant_text(CONVERSATION_CLOSING_MESSAGE)
        self._publisher.publish("session.completed", {"reason": resolved})
        if self._stream is None:
            return
        self._stream.publish_session_completed(
            reason=resolved,
            completed_at=self._clock(),
        )

    def emit_ai_turn_completed(
        self,
        turn_id: str,
        turn_seq: int,
        response_id: str,
        text: str,
        audio_started_at: datetime,
        audio_completed_at: datetime,
    ) -> None:
        """synthesis의 on_completed 콜백이 호출한다(§11 호환 수정 5)."""

        if self._stream is None:
            return
        self._stream.publish_ai_turn_completed(
            turn_id=turn_id,
            turn_seq=turn_seq,
            response_id=response_id,
            text=text,
            audio_started_at=audio_started_at,
            audio_completed_at=audio_completed_at,
        )


def _reason_value(response: DialogueTurnResponse) -> str | None:
    return (
        response.termination_reason.value
        if response.termination_reason is not None
        else None
    )


class RealtimeDialogueSession:
    """Everything one connection's realtime dialogue owns, behind one handle."""

    def __init__(
        self,
        *,
        audio_sink: SessionSttPipeline,
        manager: RealtimeDialogueManager,
        state: DialogueSessionState,
        synthesis: SpeechSynthesisService | None = None,
        stream_producer: ConversationStreamProducer | None = None,
    ) -> None:
        self.audio_sink = audio_sink
        self.manager = manager
        self.state = state
        self.synthesis = synthesis
        self.stream_producer = stream_producer

    def is_response_cancelled(self, response_id: str) -> bool:
        return self.manager.is_response_cancelled(response_id)

    def is_known_turn(self, turn_id: str) -> bool:
        return self.manager.is_known_turn(turn_id)

    async def aclose(self) -> None:
        """Idempotent — the sink is also closed via the audio bridge path."""

        if self.synthesis is not None:
            await self.synthesis.aclose()
        await self.audio_sink.aclose()
        # Stream은 마지막에 닫는다 — 위 정리 중 발행된 이벤트도 배출하도록.
        if self.stream_producer is not None:
            await self.stream_producer.aclose()


SttPipelineFactory = Callable[..., SessionSttPipeline]
SnapshotReader = Callable[[str], Awaitable[ContextSnapshot | None]]
# (speaker, publisher, response_registry, on_completed) -> service.
SynthesisServiceFactory = Callable[..., SpeechSynthesisService]
# (publisher) -> producer. 발행이 필요할 때만 주입(미주입 시 발행 없음).
StreamProducerFactory = Callable[
    [ConversationEventPublisher], ConversationStreamProducer
]


async def create_realtime_dialogue_session(
    *,
    publisher: ConversationEventPublisher,
    conversation_id: str,
    read_snapshot: SnapshotReader,
    default_snapshot: Callable[[str], ContextSnapshot],
    dialogue_pipeline: DialogueTurnPipeline,
    termination_policy: DialogueTerminationPolicy,
    is_epoch_current: EpochCurrentCheck,
    stt_pipeline_factory: SttPipelineFactory,
    logger: logging.Logger,
    speaker: SpeakerHandle | None = None,
    synthesis_service_factory: SynthesisServiceFactory | None = None,
    stream_producer_factory: StreamProducerFactory | None = None,
    low_confidence_threshold: float = 0.45,
    low_confidence_reask_limit: int = 1,
) -> RealtimeDialogueSession:
    """Assemble one connection's dialogue session (T11 read-once included).

    ``stt_pipeline_factory(publisher, turn_sequencer, on_final, on_no_speech,
    is_output_active)`` keeps the STT wiring (recognizer/VAD/config) where it
    already lives — ``main.py`` — instead of duplicating it here.

    ``speaker`` is the outbound AI voice; it is ``None`` for an audio-less
    offer, in which case the session runs text-only (no synthesis).
    """

    snapshot = await read_snapshot(conversation_id)
    if snapshot is None:
        # 대화 불능보다 기본 주제로 살아있는 쪽이 낫다(W03 plan §7-C).
        logger.warning(
            "context snapshot missing; falling back to the default topic",
            extra={"conversation_id": conversation_id},
        )
        snapshot = default_snapshot(conversation_id)

    state = DialogueSessionState(snapshot)
    # 재연결 시 저장된 마지막 turnSeq 다음부터 발급한다(§11-A). 아이 턴과 AI 턴이
    # 이 단일 수열을 공유한다(§4).
    turn_sequencer = TurnSequencer(
        start_seq=snapshot.conversation.last_turn_seq + 1
    )
    response_registry = ResponseRegistry()

    stream_producer: ConversationStreamProducer | None = None
    if stream_producer_factory is not None:
        stream_producer = stream_producer_factory(publisher)
        stream_producer.start()

    manager = RealtimeDialogueManager(
        dialogue_pipeline=dialogue_pipeline,
        termination_policy=termination_policy,
        publisher=publisher,
        state=state,
        turn_sequencer=turn_sequencer,
        response_registry=response_registry,
        is_epoch_current=is_epoch_current,
        logger=logger,
        stream_producer=stream_producer,
        low_confidence_threshold=low_confidence_threshold,
        low_confidence_reask_limit=low_confidence_reask_limit,
    )

    synthesis: SpeechSynthesisService | None = None
    if speaker is not None and synthesis_service_factory is not None:
        # synthesis는 완료 시각을 manager로 넘겨 AI_TURN_COMPLETED를 발행하고,
        # manager는 synthesis.speak로 발화를 시작한다 — 순환은 speak를 나중에 건다.
        synthesis = synthesis_service_factory(
            speaker, publisher, response_registry, manager.emit_ai_turn_completed
        )
        manager.set_speak(synthesis.speak)

    if stream_producer is not None:
        stream_producer.publish_session_started(datetime.now(timezone.utc))

    # 연결이 열린 직후 AI가 먼저 첫 질문을 던진다 — DataChannel은 이 조립이
    # 실행되는 시점(on_data_channel_received)에 이미 열려 있다.
    manager.open_conversation()

    audio_sink = stt_pipeline_factory(
        publisher,
        turn_sequencer=turn_sequencer,
        on_final=manager.handle_child_turn,
        on_no_speech=manager.handle_no_speech,
        # AI가 말하는 동안 입력을 받지 않는다(ADR-020). 오디오 없는 연결은 합성이
        # 없으므로 게이트도 없다 — 그때는 애초에 재생될 오디오가 없다.
        # is_speaking은 프로퍼티다 — 여기서 평가하면 조립 시점의 bool이 박제된다.
        # 프레임마다 현재 값을 읽도록 반드시 콜러블로 감싼다(실 스택에서 발견된 결함).
        is_output_active=(
            None if synthesis is None else (lambda: synthesis.is_speaking)
        ),
    )
    return RealtimeDialogueSession(
        audio_sink=audio_sink,
        manager=manager,
        state=state,
        synthesis=synthesis,
        stream_producer=stream_producer,
    )
