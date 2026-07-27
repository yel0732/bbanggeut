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

from app.dialogue.pipeline import (
    DialoguePipelineError,
    DialogueTurnDecision,
    DialogueTurnPipeline,
    DialogueTurnRequest,
    DialogueTurnResponse,
)
from app.dialogue.policy.recovery import (
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


def _default_request_id_factory() -> str:
    return f"req_{uuid.uuid4().hex}"


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
            _TopicState(snapshot=topic, slots=topic.initial_slots.model_copy(deep=True))
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

        topic = state.current_topic
        request = DialogueTurnRequest(
            request_id=self._request_id_factory(),
            conversation_id=state.snapshot.conversation_id,
            child_answer=result.text,
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
            return

        published = self._publish_assistant_text(response.assistant_message)
        if not published:
            return

        if response.decision is DialogueTurnDecision.SWITCH_TOPIC:
            topic.slots = response.updated_slots  # 끝난 주제의 최종 슬롯
            state.advance_topic()
            # 전환 질문은 새 주제의 첫 질문으로 센다.
            state.current_topic.question_count = 1
        else:
            topic.slots = response.updated_slots
            topic.question_count += 1
        state.remember_question(response.assistant_question)

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
            state.terminated = True
            self._logger.info("conversation terminated: no target left on no-response")
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
            return

        if decision.recovery_prompt is not None:
            prompt_text = decision.recovery_prompt.text
        else:
            # SKIP: 다음 목표의 규칙 기반 질문으로 넘어간다(ADR-017).
            prompt_text = build_fallback_question(decision.next_target).text
        if self._publish_assistant_text(prompt_text):
            # 재질문은 질문 수에 세지 않는다(W03 plan §7-E) — 상태만 갱신.
            self._state.remember_question(prompt_text)

    # --- publishing (T15) ----------------------------------------------------

    def _publish_assistant_text(self, text: str) -> bool:
        """Issue an AI turn + response and publish it, unless it must be discarded.

        The re-check happens here, after any await points, because the LLM
        call cannot be interrupted — cancelled/stale results are dropped at
        the last gate before the wire (ADR-012, W03 완료 조건).
        """

        state = self._state
        ai_turn = self._turns.next_turn()
        response_id = self._responses.issue()

        if self._responses.is_cancelled(response_id) or not self._is_epoch_current():
            self._logger.info(
                "assistant response discarded before publish",
                extra={"turn_id": ai_turn.turn_id, "response_id": response_id},
            )
            return False

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
        return True


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
    ) -> None:
        self.audio_sink = audio_sink
        self.manager = manager
        self.state = state
        self.synthesis = synthesis

    def is_response_cancelled(self, response_id: str) -> bool:
        return self.manager.is_response_cancelled(response_id)

    def is_known_turn(self, turn_id: str) -> bool:
        return self.manager.is_known_turn(turn_id)

    async def aclose(self) -> None:
        """Idempotent — the sink is also closed via the audio bridge path."""

        if self.synthesis is not None:
            await self.synthesis.aclose()
        await self.audio_sink.aclose()


SttPipelineFactory = Callable[..., SessionSttPipeline]
SnapshotReader = Callable[[str], Awaitable[ContextSnapshot | None]]
# (speaker, publisher, response_registry) -> service. 음성 출력이 있을 때만 만든다.
SynthesisServiceFactory = Callable[
    [SpeakerHandle, ConversationEventPublisher, ResponseRegistry],
    SpeechSynthesisService,
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
) -> RealtimeDialogueSession:
    """Assemble one connection's dialogue session (T11 read-once included).

    ``stt_pipeline_factory(publisher, turn_sequencer, on_final, on_no_speech)``
    keeps the STT wiring (recognizer/VAD/config) where it already lives —
    ``main.py`` — instead of duplicating it here.

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
    turn_sequencer = TurnSequencer()  # 아이 턴과 AI 턴이 공유하는 단일 수열(§4)
    response_registry = ResponseRegistry()

    synthesis: SpeechSynthesisService | None = None
    if speaker is not None and synthesis_service_factory is not None:
        synthesis = synthesis_service_factory(speaker, publisher, response_registry)

    manager = RealtimeDialogueManager(
        dialogue_pipeline=dialogue_pipeline,
        termination_policy=termination_policy,
        publisher=publisher,
        state=state,
        turn_sequencer=turn_sequencer,
        response_registry=response_registry,
        is_epoch_current=is_epoch_current,
        logger=logger,
        speak=synthesis.speak if synthesis is not None else None,
    )
    audio_sink = stt_pipeline_factory(
        publisher,
        turn_sequencer=turn_sequencer,
        on_final=manager.handle_child_turn,
        on_no_speech=manager.handle_no_speech,
    )
    return RealtimeDialogueSession(
        audio_sink=audio_sink, manager=manager, state=state, synthesis=synthesis
    )
