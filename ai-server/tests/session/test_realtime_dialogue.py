"""Realtime dialogue manager tests (T11~T15).

The dialogue pipeline itself is DIARY-1 code with its own test suite — here it
is a scripted fake, and what is under test is exactly W03's own work: session
state, identifier lifecycle, the cancel/epoch gate, and event publishing.
"""

import asyncio
import logging
from datetime import datetime, timezone

import pytest

from app.dialogue.pipeline import (
    DialoguePipelineError,
    DialoguePipelineFailureCode,
    DialogueTurnDecision,
    DialogueTurnResponse,
)
from app.dialogue.policy.termination import (
    DialogueTerminationPolicy,
    DialogueTerminationReason,
)
from app.dialogue.policy.target import QuestionTarget
from app.dialogue.question.fallback import (
    QuestionGenerationSource,
    QuestionGenerationStatus,
)
from app.dialogue.schemas import DiarySlots
from app.events.outbound import ConversationEventPublisher
from app.realtime.context_snapshot import ContextSnapshot, default_context_snapshot
from app.session.realtime_dialogue import (
    DialogueSessionState,
    RealtimeDialogueManager,
    create_realtime_dialogue_session,
)
from app.session.response_registry import ResponseRegistry
from app.speech.recognizer import SttResult
from app.speech.turn_state import SttTurn, TurnSequencer
from app.webrtc.data_channel import FakeDataChannel

_LOGGER = logging.getLogger("test.session.realtime_dialogue")

_TWO_TOPIC_SNAPSHOT = ContextSnapshot.model_validate(
    {
        "contextVersion": 1,
        "conversationId": "conv_1",
        "child": {"displayName": "다온"},
        "conversation": {
            "goal": "DIARY",
            "language": "ko-KR",
            "currentTopic": "GAME",
            "totalMaxQuestionCount": 12,
        },
        "recentConversationSummary": None,
        "topics": [
            {
                "type": "GAME",
                "order": 1,
                "maxQuestionCount": 6,
                "games": [
                    {
                        "gameSessionId": "b3b10000-0000-0000-0000-000000000001",
                        "gameId": 1,
                        "score": 820,
                        "playDurationSeconds": 180,
                        "completedAt": "2026-07-27T10:00:00",
                    }
                ],
                "initialSlots": {"event": "과일 게임", "people": []},
            },
            {
                "type": "DAILY",
                "order": 2,
                "maxQuestionCount": 6,
                "games": [],
                "initialSlots": {"event": None, "people": []},
            },
        ],
        "policy": {"oneQuestionAtATime": True, "avoidPressureQuestions": True},
    }
)


def _continue_response(question: str = "그때 기분이 어땠어?") -> DialogueTurnResponse:
    return DialogueTurnResponse(
        updated_slots=DiarySlots(event="과일 게임", emotion=None),
        decision=DialogueTurnDecision.CONTINUE_TOPIC,
        assistant_message=f"재미있었구나! {question}",
        assistant_question=question,
        assistant_target=QuestionTarget.EMOTION,
        question_generation_status=QuestionGenerationStatus.GENERATED,
        question_generation_source=QuestionGenerationSource.LLM,
    )


def _switch_response(question: str = "오늘 있었던 다른 일도 이야기해 줄래?") -> DialogueTurnResponse:
    return DialogueTurnResponse(
        updated_slots=DiarySlots(event="과일 게임", emotion="신남"),
        decision=DialogueTurnDecision.SWITCH_TOPIC,
        termination_reason=DialogueTerminationReason.REQUIRED_SLOTS_FILLED,
        assistant_message=f"게임 이야기 고마워! {question}",
        assistant_question=question,
        assistant_target=QuestionTarget.EVENT,
        question_generation_status=QuestionGenerationStatus.TEMPLATE_USED,
        question_generation_source=QuestionGenerationSource.RULE_BASED_TEMPLATE,
    )


def _switch_response_with_next_slots() -> DialogueTurnResponse:
    return DialogueTurnResponse(
        updated_slots=DiarySlots(event="game story", emotion="excited"),
        next_topic_slots=DiarySlots(
            event="playground swing missed",
            emotion="upset",
            emotionReason="because the swing was unavailable",
        ),
        decision=DialogueTurnDecision.SWITCH_TOPIC,
        termination_reason=DialogueTerminationReason.CHILD_REQUESTED_TOPIC_SWITCH,
        assistant_message=(
            "I will listen to the playground story instead of the game story. "
            "That must have felt upsetting. Can you tell me a little more?"
        ),
        assistant_question="Can you tell me a little more?",
        assistant_target=QuestionTarget.MEMORABLE_MOMENT,
        question_generation_status=QuestionGenerationStatus.TEMPLATE_USED,
        question_generation_source=QuestionGenerationSource.RULE_BASED_TEMPLATE,
    )


def _terminate_response() -> DialogueTurnResponse:
    return DialogueTurnResponse(
        updated_slots=DiarySlots(event="과일 게임", emotion="신남"),
        decision=DialogueTurnDecision.TERMINATE_SESSION,
        termination_reason=DialogueTerminationReason.MAX_QUESTION_REACHED,
        closing_message=(
            "좋아! 내일 그네 타고 나서 또 만나자. "
            "오늘 이야기해줘서 고마워. 오늘 이야기를 일기로 남겨볼래?"
        ),
    )


class _FakeDialoguePipeline:
    """Scripted DialogueTurnPipeline stand-in; records every request."""

    def __init__(self, outcomes: list[DialogueTurnResponse | Exception] | None = None) -> None:
        self._outcomes = list(outcomes or [])
        self.requests: list = []
        self.entered = asyncio.Event()
        self.release: asyncio.Event | None = None

    async def process(self, request):
        self.requests.append(request)
        self.entered.set()
        if self.release is not None:
            await self.release.wait()
        if not self._outcomes:
            return _continue_response()
        outcome = self._outcomes.pop(0)
        if isinstance(outcome, Exception):
            raise outcome
        return outcome


def _build_manager(
    pipeline: _FakeDialoguePipeline | None = None,
    snapshot: ContextSnapshot = _TWO_TOPIC_SNAPSHOT,
    *,
    epoch_current: bool = True,
):
    channel = FakeDataChannel()
    event_ids = iter(f"evt_{index}" for index in range(1, 500))
    publisher = ConversationEventPublisher(
        channel,
        conversation_id="conv_1",
        realtime_session_id="rt_1",
        session_epoch=1,
        logger=_LOGGER,
        clock=lambda: datetime(2026, 7, 27, tzinfo=timezone.utc),
        event_id_factory=lambda: next(event_ids),
    )
    turn_ids = iter(f"turn_{index}" for index in range(1, 500))
    response_ids = iter(f"resp_{index}" for index in range(1, 500))
    state = DialogueSessionState(snapshot)
    registry = ResponseRegistry(response_id_factory=lambda: next(response_ids))
    pipeline = pipeline or _FakeDialoguePipeline()
    sequencer = TurnSequencer(turn_id_factory=lambda: next(turn_ids))
    manager = RealtimeDialogueManager(
        dialogue_pipeline=pipeline,
        termination_policy=DialogueTerminationPolicy(
            required_targets=frozenset(QuestionTarget)
        ),
        publisher=publisher,
        state=state,
        turn_sequencer=sequencer,
        response_registry=registry,
        is_epoch_current=lambda: epoch_current,
        logger=_LOGGER,
    )
    return manager, state, channel, pipeline, registry, sequencer


def _child_turn(sequencer: TurnSequencer) -> SttTurn:
    return sequencer.next_turn()


def _stt_result(text: str = "과일 게임이 재미있었어.") -> SttResult:
    return SttResult(text=text, audio_seconds=2.0, no_speech_prob=0.0, avg_logprob=-0.3)


def test_dialogue_state_restores_persisted_question_counts() -> None:
    raw = _TWO_TOPIC_SNAPSHOT.model_dump(mode="json", by_alias=True)
    raw["conversation"]["questionCount"] = 4
    raw["topics"][0]["questionCount"] = 4
    snapshot = ContextSnapshot.model_validate(raw)

    state = DialogueSessionState(snapshot)

    assert state.current_topic.question_count == 4
    assert state.total_question_count == 4


@pytest.mark.asyncio
async def test_reconnect_restores_topic_history_and_current_question() -> None:
    raw = _TWO_TOPIC_SNAPSHOT.model_dump(mode="json", by_alias=True)
    raw["conversation"]["questionCount"] = 2
    raw["topics"][0]["questionCount"] = 2
    raw["topics"][0]["topicHistory"] = [
        {
            "assistantMessage": "과일을 잘랐구나! 기분이 어땠어?",
            "assistantQuestion": "기분이 어땠어?",
            "childAnswer": "정말 신났어",
        }
    ]
    raw["topics"][0]["currentAssistantMessage"] = (
        "정말 신났구나! 어떤 점 때문에 신났어?"
    )
    raw["topics"][0]["currentQuestion"] = "어떤 점 때문에 신났어?"
    raw["topics"][0]["currentTarget"] = "emotionReason"
    snapshot = ContextSnapshot.model_validate(raw)
    pipeline = _FakeDialoguePipeline()
    manager, state, _, _, _, sequencer = _build_manager(
        pipeline,
        snapshot,
    )

    await manager.handle_child_turn(
        _child_turn(sequencer),
        _stt_result("수박을 한 번에 잘라서"),
    )

    request = pipeline.requests[0]
    assert state.last_question == "그때 기분이 어땠어?"
    assert request.current_question == "어떤 점 때문에 신났어?"
    assert request.current_target is QuestionTarget.EMOTION_REASON
    assert len(request.topic_history) == 1
    assert request.topic_history[0].child_answer == "정말 신났어"


class TestChildTurn:
    @pytest.mark.asyncio
    async def test_publishes_created_then_text_final_with_one_response_id(self) -> None:
        manager, _, channel, _, _, sequencer = _build_manager()

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert [event["type"] for event in channel.sent] == [
            "assistant.response.created",
            "assistant.text.final",
            "conversation.slots.updated",
        ]
        created, text_final, _slots = channel.sent
        assert created["responseId"] == text_final["responseId"] == "resp_1"
        assert created["turnId"] == text_final["turnId"] == "turn_2"
        assert created["payload"] == {}

    @pytest.mark.asyncio
    async def test_text_carries_the_full_spoken_message(self) -> None:
        """W03 plan §7-F: TTS(W04)가 읽을 텍스트는 호응+질문 전체다."""

        manager, _, channel, _, _, sequencer = _build_manager()

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert channel.sent[1]["payload"]["text"] == "재미있었구나! 그때 기분이 어땠어?"

    @pytest.mark.asyncio
    async def test_ai_turn_continues_the_shared_turn_sequence(self) -> None:
        """§4: 아이 턴과 AI 턴은 별도 턴이지만 하나의 turnSeq 수열을 쓴다."""

        manager, _, channel, _, _, sequencer = _build_manager()
        child = _child_turn(sequencer)  # turnSeq 1

        await manager.handle_child_turn(child, _stt_result())

        assert child.turn_seq == 1
        assert channel.sent[0]["turnSeq"] == 2

    @pytest.mark.asyncio
    async def test_next_request_carries_the_last_question_and_updated_slots(self) -> None:
        pipeline = _FakeDialoguePipeline([_continue_response("어디서 했어?"), _continue_response()])
        manager, _, _, _, _, sequencer = _build_manager(pipeline)

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        await manager.handle_child_turn(_child_turn(sequencer), _stt_result("집에서 했어."))

        first, second = pipeline.requests
        assert first.current_question is None  # 첫 턴은 질문 없이(관례 확인, plan §1)
        assert second.current_question == "어디서 했어?"
        assert second.current_slots.event == "과일 게임"
        assert second.question_count == 1
        assert "어디서 했어?" in second.recent_questions

    @pytest.mark.asyncio
    async def test_game_topic_context_reaches_the_pipeline(self) -> None:
        pipeline = _FakeDialoguePipeline()
        manager, _, _, _, _, sequencer = _build_manager(pipeline)

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        request = pipeline.requests[0]
        assert request.game_context is not None
        assert request.game_context.games[0].score == 820
        assert request.max_question_count == 6

    @pytest.mark.asyncio
    async def test_switch_topic_resets_per_topic_state(self) -> None:
        pipeline = _FakeDialoguePipeline([_switch_response(), _continue_response()])
        manager, state, _, _, _, sequencer = _build_manager(pipeline)

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        assert state.current_topic.snapshot.type.value == "DAILY"
        assert state.current_topic.question_count == 1  # 전환 질문 = 새 주제 1번 질문

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result("산책했어."))
        request = pipeline.requests[1]
        assert request.current_topic.value == "DAILY"
        assert request.current_slots.event is None  # 새 주제는 initialSlots에서 시작
        assert request.next_topic is None

    @pytest.mark.asyncio
    async def test_switch_topic_applies_next_topic_slots(self) -> None:
        pipeline = _FakeDialoguePipeline(
            [_switch_response_with_next_slots(), _continue_response()]
        )
        manager, state, _, _, _, sequencer = _build_manager(pipeline)

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        assert state.current_topic.snapshot.type.value == "DAILY"
        assert state.current_topic.slots.event == "playground swing missed"
        assert state.current_topic.slots.emotion == "upset"

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result("more story"))
        request = pipeline.requests[1]
        assert request.current_slots.event == "playground swing missed"
        assert request.current_slots.emotion == "upset"

    @pytest.mark.asyncio
    async def test_terminate_says_goodbye_then_signals_and_blocks_further_turns(self) -> None:
        pipeline = _FakeDialoguePipeline([_terminate_response()])
        manager, state, channel, _, _, sequencer = _build_manager(pipeline)

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        await manager.handle_child_turn(_child_turn(sequencer), _stt_result("또 하고 싶어."))

        assert state.terminated is True
        assert [event["type"] for event in channel.sent] == [
            "assistant.response.created",
            "assistant.text.final",
            "session.completed",
        ]
        assert channel.sent[1]["payload"]["text"] == (
            "좋아! 내일 그네 타고 나서 또 만나자. "
            "오늘 이야기해줘서 고마워. 오늘 이야기를 일기로 남겨볼래?"
        )
        # 자막 동기화를 위해 인사 텍스트와 오디오는 같은 responseId를 공유한다.
        assert channel.sent[0]["responseId"] == channel.sent[1]["responseId"]
        assert channel.sent[2]["payload"]["reason"] == "MAX_QUESTION_REACHED"
        assert len(pipeline.requests) == 1  # 종료 후 턴은 파이프라인에 닿지 않는다

    @pytest.mark.asyncio
    async def test_pipeline_failure_publishes_a_recoverable_error(self) -> None:
        pipeline = _FakeDialoguePipeline(
            [DialoguePipelineError(DialoguePipelineFailureCode.QUESTION_GENERATION_FAILED)]
        )
        manager, state, channel, _, _, sequencer = _build_manager(pipeline)
        child = _child_turn(sequencer)

        await manager.handle_child_turn(child, _stt_result())

        assert [event["type"] for event in channel.sent] == ["error"]
        assert channel.sent[0]["payload"]["code"] == "LLM_TIMEOUT"
        assert channel.sent[0]["payload"]["recoverable"] is True
        assert channel.sent[0]["turnId"] == child.turn_id
        assert state.terminated is False  # 대화는 계속될 수 있다


def _low_confidence_result(text: str = "심부란 노랐어.") -> SttResult:
    # exp(-1.5) ≈ 0.223 < 기본 임계값 0.35 — 심하게 깨진 전사 수준의 신뢰도.
    return SttResult(text=text, audio_seconds=2.0, no_speech_prob=0.0, avg_logprob=-1.5)


class TestLowConfidenceReask:
    """§22 STT_LOW_CONFIDENCE: 낮은 신뢰도 Final은 대화 대신 확인 질문으로."""

    @pytest.mark.asyncio
    async def test_low_confidence_final_reasks_instead_of_running_dialogue(self) -> None:
        manager, _, channel, pipeline, _, sequencer = _build_manager()
        child = _child_turn(sequencer)

        await manager.handle_child_turn(child, _low_confidence_result())

        assert pipeline.requests == []  # 오인식 의심 텍스트는 슬롯 분석에 넣지 않는다
        assert [event["type"] for event in channel.sent] == [
            "error",
            "assistant.response.created",
            "assistant.text.final",
            "conversation.slots.updated",
        ]
        error = channel.sent[0]
        assert error["payload"]["code"] == "STT_LOW_CONFIDENCE"
        assert error["payload"]["recoverable"] is True
        assert error["turnId"] == child.turn_id
        assert channel.sent[2]["payload"]["text"] == "괜찮아, 천천히 다시 말해 줄래?"

    @pytest.mark.asyncio
    async def test_reask_counts_no_question_and_keeps_the_pending_question(self) -> None:
        """재발화는 원래 질문에 대한 답으로 평가돼야 한다 — 상태 불변."""

        manager, state, _, _, _, sequencer = _build_manager()
        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        question_count = state.current_topic.question_count
        last_question = state.last_question

        await manager.handle_child_turn(_child_turn(sequencer), _low_confidence_result())

        assert state.current_topic.question_count == question_count
        assert state.last_question == last_question
        assert state.recent_questions == [last_question]

    @pytest.mark.asyncio
    async def test_reask_limit_lets_the_next_low_confidence_final_proceed(self) -> None:
        """연속 상한(기본 1) 도달 시 낮은 신뢰도라도 진행 — 무한 되묻기 방지.

        그때는 sttConfidence가 파이프라인으로 전달돼 분석 LLM이 보수적으로
        해석한다(2순위 연계).
        """

        manager, _, _, pipeline, _, sequencer = _build_manager()

        await manager.handle_child_turn(_child_turn(sequencer), _low_confidence_result())
        await manager.handle_child_turn(_child_turn(sequencer), _low_confidence_result())

        assert len(pipeline.requests) == 1
        assert pipeline.requests[0].stt_confidence == pytest.approx(0.2231, abs=1e-3)

    @pytest.mark.asyncio
    async def test_confident_final_resets_the_reask_counter(self) -> None:
        manager, state, _, pipeline, _, sequencer = _build_manager()

        await manager.handle_child_turn(_child_turn(sequencer), _low_confidence_result())
        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        await manager.handle_child_turn(_child_turn(sequencer), _low_confidence_result())

        assert len(pipeline.requests) == 1  # 확신 턴만 파이프라인에 들어갔다
        assert state.consecutive_low_confidence_reasks == 1  # 세 번째가 다시 되물음

    @pytest.mark.asyncio
    @pytest.mark.parametrize("confidence_logprob", [-0.65, -0.51, -0.40])
    async def test_real_speech_confidence_band_is_not_reasked(
        self,
        confidence_logprob: float,
    ) -> None:
        """실제 마이크 음성의 정확 전사대(≈0.60~0.67)는 되묻지 않는다.

        회귀: 합성 음성 기준(0.67)으로 보정했을 때 정상 대화가 매 턴 되묻기에
        걸렸다 — 재발화해도 같은 문장이라 되묻기가 정확도를 높이지 못했다.
        """

        manager, _, _, pipeline, _, sequencer = _build_manager()
        result = SttResult(
            text="오늘 친구랑 모래성을 쌓았어",
            audio_seconds=2.0,
            no_speech_prob=0.0,
            avg_logprob=confidence_logprob,
        )

        await manager.handle_child_turn(_child_turn(sequencer), result)

        assert len(pipeline.requests) == 1

    @pytest.mark.asyncio
    async def test_missing_confidence_skips_the_gate(self) -> None:
        """avg_logprob가 없으면(신뢰도 미상) 되묻지 않고 진행한다."""

        manager, _, _, pipeline, _, sequencer = _build_manager()
        result = SttResult(text="블록 놀이 했어.", audio_seconds=2.0)

        await manager.handle_child_turn(_child_turn(sequencer), result)

        assert len(pipeline.requests) == 1
        assert pipeline.requests[0].stt_confidence is None

    @pytest.mark.asyncio
    async def test_confident_final_passes_its_confidence_to_the_pipeline(self) -> None:
        manager, _, _, pipeline, _, sequencer = _build_manager()

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert pipeline.requests[0].stt_confidence == pytest.approx(0.7408, abs=1e-3)


class TestCancellationBoundary:
    @pytest.mark.asyncio
    async def test_response_cancelled_mid_llm_call_is_never_published(self) -> None:
        """ADR-012: LLM 결과는 기다렸다가 버린다 — 발행 직전 재검증."""

        pipeline = _FakeDialoguePipeline([_continue_response()])
        pipeline.release = asyncio.Event()
        manager, _, channel, _, registry, sequencer = _build_manager(pipeline)

        task = asyncio.create_task(
            manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        )
        await asyncio.wait_for(pipeline.entered.wait(), timeout=1)
        # LLM 호출이 진행 중인 동안 다음에 발급될 응답이 취소된다(DIARY-4의
        # barge-in이 하게 될 일을 훅으로 재현).
        original_issue = registry.issue

        def cancelling_issue() -> str:
            response_id = original_issue()
            registry.cancel(response_id)
            return response_id

        registry.issue = cancelling_issue  # type: ignore[method-assign]
        pipeline.release.set()
        await task

        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_stale_epoch_result_is_never_published(self) -> None:
        manager, _, channel, _, _, sequencer = _build_manager(epoch_current=False)

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_guard_hooks_reflect_the_registry_and_ledger(self) -> None:
        manager, _, channel, _, registry, sequencer = _build_manager()
        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        response_id = channel.sent[0]["responseId"]
        ai_turn_id = channel.sent[0]["turnId"]

        assert manager.is_known_turn(ai_turn_id) is True
        assert manager.is_known_turn("turn_ghost") is False
        assert manager.is_response_cancelled(response_id) is False
        registry.cancel(response_id)
        assert manager.is_response_cancelled(response_id) is True


class TestNoSpeechLadder:
    """W02의 신호를 DIARY-1 사다리(1 단순화→2 재요청→3 선택지→4 건너뛰기→5 종료)에 연결."""

    def test_first_no_speech_publishes_a_simplified_question(self) -> None:
        manager, state, channel, _, _, _ = _build_manager()

        manager.handle_no_speech()

        assert [event["type"] for event in channel.sent] == [
            "assistant.response.created",
            "assistant.text.final",
            "conversation.slots.updated",
        ]
        assert "어땠어" in channel.sent[1]["payload"]["text"]
        assert state.consecutive_no_responses == 1

    def test_second_no_speech_asks_to_repeat(self) -> None:
        manager, _, channel, _, _, _ = _build_manager()

        manager.handle_no_speech()
        manager.handle_no_speech()

        # 회차마다 created·text.final·slots.updated 3개가 나가므로 2회차 텍스트는 5번째.
        assert channel.sent[4]["payload"]["text"] == "괜찮아, 천천히 다시 말해 줄래?"

    def test_fifth_no_speech_terminates_with_a_goodbye_and_signal(self) -> None:
        manager, state, channel, _, _, _ = _build_manager()

        for _ in range(5):
            manager.handle_no_speech()

        assert state.terminated is True
        # 1~4회차는 각각 3개(created·text.final·slots.updated), 5회차는 인사 2개 +
        # 종료 신호 1개. 인사는 질문이 아니라 슬롯 이벤트를 동반하지 않는다.
        assert len(channel.sent) == 15
        assert [event["type"] for event in channel.sent[-3:]] == [
            "assistant.response.created",
            "assistant.text.final",
            "session.completed",
        ]
        assert channel.sent[-1]["payload"]["reason"] == "INACTIVITY_TIMEOUT"

    def test_recovery_prompt_does_not_consume_the_question_budget(self) -> None:
        """재질문은 질문 수에 세지 않는다(plan §7-E)."""

        manager, state, _, _, _, _ = _build_manager()

        manager.handle_no_speech()

        assert state.current_topic.question_count == 0
        assert state.last_question is not None

    @pytest.mark.asyncio
    async def test_a_child_answer_resets_the_ladder(self) -> None:
        manager, state, _, _, _, sequencer = _build_manager()
        manager.handle_no_speech()
        manager.handle_no_speech()

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert state.consecutive_no_responses == 0

    @pytest.mark.asyncio
    async def test_skipped_targets_reach_the_answer_pipeline(self) -> None:
        """건너뛴 목표가 답변 턴의 파이프라인 요청까지 전달된다."""

        pipeline = _FakeDialoguePipeline()
        manager, state, _, _, _, sequencer = _build_manager(pipeline)
        state.current_topic.skipped_targets = frozenset({QuestionTarget.EMOTION})

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert pipeline.requests[0].skipped_targets == frozenset(
            {QuestionTarget.EMOTION}
        )

    @pytest.mark.asyncio
    async def test_pipeline_skipped_targets_are_kept_for_the_next_turn(self) -> None:
        response = _continue_response()
        response = response.model_copy(
            update={
                "updated_skipped_targets": frozenset(
                    {QuestionTarget.MEMORABLE_MOMENT}
                )
            }
        )
        pipeline = _FakeDialoguePipeline([response, _continue_response()])
        manager, state, _, _, _, sequencer = _build_manager(pipeline)

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert state.current_topic.skipped_targets == frozenset(
            {QuestionTarget.MEMORABLE_MOMENT}
        )
        assert pipeline.requests[1].skipped_targets == frozenset(
            {QuestionTarget.MEMORABLE_MOMENT}
        )

    def test_no_speech_after_termination_is_ignored(self) -> None:
        manager, state, channel, _, _, _ = _build_manager()
        state.terminated = True

        manager.handle_no_speech()

        assert channel.sent == []


class TestConversationOpening:
    """연결 직후 AI가 먼저 첫 질문을 던진다 — 아이가 먼저 말할 필요가 없다."""

    def test_opening_publishes_the_first_question_and_counts_it(self) -> None:
        manager, state, channel, _, _, _ = _build_manager()

        manager.open_conversation()

        assert [event["type"] for event in channel.sent] == [
            "assistant.response.created",
            "assistant.text.final",
            "conversation.slots.updated",
        ]
        assert channel.sent[1]["payload"]["text"] == (
            "오늘 했던 게임 중 가장 기억나는 게임은 무엇이었어?"
        )
        # 오프닝 시점의 슬롯 상태가 함께 나가 화면이 처음부터 진행도를 보여준다.
        slots_event = channel.sent[2]["payload"]
        assert slots_event["topicType"] == "GAME"
        assert slots_event["topicQuestionCount"] == 1
        assert slots_event["slots"]["event"] == "과일 게임"
        assert state.current_topic.question_count == 1
        assert state.last_question == channel.sent[1]["payload"]["text"]

    def test_opening_calls_the_game_by_name_when_there_is_one(self) -> None:
        raw = _TWO_TOPIC_SNAPSHOT.model_dump(mode="json", by_alias=True)
        raw["topics"][0]["games"][0]["gameName"] = "과일 자르기"
        manager, _, channel, _, _, _ = _build_manager(
            snapshot=ContextSnapshot.model_validate(raw)
        )

        manager.open_conversation()

        assert channel.sent[1]["payload"]["text"] == (
            "오늘 과일 자르기 했구나! 어떤 순간이 제일 재미있었어?"
        )

    def test_opening_stays_generic_when_several_games_were_played(self) -> None:
        raw = _TWO_TOPIC_SNAPSHOT.model_dump(mode="json", by_alias=True)
        raw["topics"][0]["games"][0]["gameName"] = "과일 자르기"
        second = dict(raw["topics"][0]["games"][0])
        second["gameSessionId"] = "b3b10000-0000-0000-0000-000000000002"
        second["gameId"] = 2
        second["gameName"] = "그림 맞추기"
        raw["topics"][0]["games"].append(second)
        manager, _, channel, _, _, _ = _build_manager(
            snapshot=ContextSnapshot.model_validate(raw)
        )

        manager.open_conversation()

        assert channel.sent[1]["payload"]["text"] == (
            "오늘 했던 게임 중 가장 기억나는 게임은 무엇이었어?"
        )

    def test_opening_uses_the_daily_question_without_games(self) -> None:
        manager, _, channel, _, _, _ = _build_manager(
            snapshot=default_context_snapshot("conv_daily")
        )

        manager.open_conversation()

        assert channel.sent[1]["payload"]["text"] == "오늘 있었던 일 하나를 말해 줄래?"

    def test_opening_is_idempotent(self) -> None:
        manager, state, channel, _, _, _ = _build_manager()

        manager.open_conversation()
        manager.open_conversation()

        assert len(channel.sent) == 3
        assert state.current_topic.question_count == 1

    def test_opening_skips_a_session_with_restored_progress(self) -> None:
        """스냅숏이 진행 중이던 카운트를 복원했으면 다시 열지 않는다."""

        manager, state, channel, _, _, _ = _build_manager()
        state.current_topic.question_count = 3

        manager.open_conversation()

        assert channel.sent == []

    def test_opening_emits_ai_turn_generated_with_count_one(self) -> None:
        manager, _, _, stream, _ = _build_manager_with_stream()

        manager.open_conversation()

        ai = next(c for c in stream.calls if c[0] == "AI_TURN_GENERATED")
        assert ai[9] == 1  # topic_question_count
        assert ai[5] == "GAME"

    @pytest.mark.asyncio
    async def test_assembly_opens_the_conversation(self) -> None:
        channel = FakeDataChannel()
        publisher = ConversationEventPublisher(
            channel,
            conversation_id="conv_open",
            realtime_session_id="rt_1",
            session_epoch=1,
            logger=_LOGGER,
        )

        def stt_pipeline_factory(
            publisher, *, turn_sequencer, on_final, on_no_speech, is_output_active=None
        ):
            class _SinkStub:
                async def aclose(self) -> None: ...

            return _SinkStub()

        async def read_snapshot(conversation_id: str):
            return _TWO_TOPIC_SNAPSHOT

        session = await create_realtime_dialogue_session(
            publisher=publisher,
            conversation_id="conv_open",
            read_snapshot=read_snapshot,
            default_snapshot=default_context_snapshot,
            dialogue_pipeline=_FakeDialoguePipeline(),
            termination_policy=DialogueTerminationPolicy(
                required_targets=frozenset(QuestionTarget)
            ),
            is_epoch_current=lambda: True,
            stt_pipeline_factory=stt_pipeline_factory,
            logger=_LOGGER,
        )

        assert [event["type"] for event in channel.sent] == [
            "assistant.response.created",
            "assistant.text.final",
            "conversation.slots.updated",
        ]
        assert session.state.last_question is not None
        await session.aclose()


class TestSessionAssembly:
    @pytest.mark.asyncio
    async def test_missing_snapshot_falls_back_to_the_default_topic(self) -> None:
        channel = FakeDataChannel()
        publisher = ConversationEventPublisher(
            channel,
            conversation_id="conv_x",
            realtime_session_id="rt_1",
            session_epoch=1,
            logger=_LOGGER,
        )
        captured: dict = {}

        def stt_pipeline_factory(
            publisher, *, turn_sequencer, on_final, on_no_speech, is_output_active=None
        ):
            captured.update(
                sequencer=turn_sequencer,
                on_final=on_final,
                on_no_speech=on_no_speech,
                is_output_active=is_output_active,
            )

            class _SinkStub:
                async def aclose(self) -> None: ...

            return _SinkStub()

        async def read_snapshot(conversation_id: str):
            return None

        session = await create_realtime_dialogue_session(
            publisher=publisher,
            conversation_id="conv_x",
            read_snapshot=read_snapshot,
            default_snapshot=default_context_snapshot,
            dialogue_pipeline=_FakeDialoguePipeline(),
            termination_policy=DialogueTerminationPolicy(
                required_targets=frozenset(QuestionTarget)
            ),
            is_epoch_current=lambda: True,
            stt_pipeline_factory=stt_pipeline_factory,
            logger=_LOGGER,
        )

        assert session.state.snapshot.conversation.current_topic.value == "DAILY"
        assert captured["on_final"] == session.manager.handle_child_turn
        assert captured["on_no_speech"] == session.manager.handle_no_speech
        # 합성이 없는(오디오 없는) 연결에는 재생될 오디오가 없으므로 게이트도 없다.
        assert captured["is_output_active"] is None
        await session.aclose()

    @pytest.mark.asyncio
    async def test_assembled_gate_is_a_callable_reading_live_speaking_state(self) -> None:
        """회귀(실 스택 하네스에서 발견): ``is_speaking``은 프로퍼티라 조립 시점에
        ``synthesis.is_speaking``으로 넘기면 그 순간의 bool이 박제되고, 프레임마다
        ``bool()`` 호출을 시도해 'bool is not callable'로 모든 프레임이 거부됐다.
        게이트는 콜러블이어야 하고 합성의 현재 상태를 반영해야 한다."""

        channel = FakeDataChannel()
        publisher = ConversationEventPublisher(
            channel,
            conversation_id="conv_x",
            realtime_session_id="rt_1",
            session_epoch=1,
            logger=_LOGGER,
        )
        captured: dict = {}

        def stt_pipeline_factory(
            publisher, *, turn_sequencer, on_final, on_no_speech, is_output_active=None
        ):
            captured["is_output_active"] = is_output_active

            class _SinkStub:
                async def aclose(self) -> None: ...

            return _SinkStub()

        class _SynthStub:
            def __init__(self) -> None:
                self.speaking = False

            @property
            def is_speaking(self) -> bool:
                return self.speaking

            def speak(self, *args) -> None: ...

            async def aclose(self) -> None: ...

        synth = _SynthStub()

        async def read_snapshot(conversation_id: str):
            return None

        session = await create_realtime_dialogue_session(
            publisher=publisher,
            conversation_id="conv_x",
            read_snapshot=read_snapshot,
            default_snapshot=default_context_snapshot,
            dialogue_pipeline=_FakeDialoguePipeline(),
            termination_policy=DialogueTerminationPolicy(
                required_targets=frozenset(QuestionTarget)
            ),
            is_epoch_current=lambda: True,
            stt_pipeline_factory=stt_pipeline_factory,
            logger=_LOGGER,
            speaker=object(),  # 합성 조립을 트리거하는 존재 표식
            synthesis_service_factory=lambda *args: synth,
        )

        gate = captured["is_output_active"]
        assert callable(gate)
        assert gate() is False
        synth.speaking = True
        assert gate() is True  # 박제된 bool이 아니라 현재 값을 읽는다
        await session.aclose()

    @pytest.mark.asyncio
    async def test_child_speech_never_reaches_the_logs(self, caplog) -> None:
        manager, _, _, _, _, sequencer = _build_manager(
            _FakeDialoguePipeline([_terminate_response()])
        )

        with caplog.at_level(logging.DEBUG):
            await manager.handle_child_turn(
                _child_turn(sequencer), _stt_result("우리집 강아지랑 놀았어.")
            )

        assert "강아지" not in caplog.text


class TestSpeakWiring:
    """W04: 텍스트 발행 직후 음성 합성이 트리거되는지(주입된 speak 콜백)."""

    @pytest.mark.asyncio
    async def test_speak_is_called_after_text_final_with_matching_ids(self) -> None:
        spoken: list = []
        manager, _, channel, _, _, sequencer = _build_manager()
        manager._speak = lambda t, s, r, text: spoken.append((t, s, r, text))  # noqa: SLF001

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        text_final = channel.sent[1]
        assert len(spoken) == 1
        turn_id, turn_seq, response_id, text = spoken[0]
        assert turn_id == text_final["turnId"]
        assert turn_seq == text_final["turnSeq"]
        assert response_id == text_final["responseId"]
        assert text == text_final["payload"]["text"]

    @pytest.mark.asyncio
    async def test_speak_is_not_called_when_response_is_discarded(self) -> None:
        """취소·낮은 epoch로 발행이 막히면 합성도 시작하지 않는다."""

        spoken: list = []
        manager, _, channel, _, _, sequencer = _build_manager(epoch_current=False)
        manager._speak = lambda *a: spoken.append(a)  # noqa: SLF001

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert channel.sent == []
        assert spoken == []

    def test_no_speak_callback_keeps_text_only_behaviour(self) -> None:
        """speak 미주입 시 기존 텍스트 전용 동작(회귀)."""

        manager, _, channel, _, _, sequencer = _build_manager()
        # _speak은 기본 None
        manager.handle_no_speech()

        assert [e["type"] for e in channel.sent] == [
            "assistant.response.created",
            "assistant.text.final",
            "conversation.slots.updated",
        ]


class _RecordingStreamProducer:
    """Captures publish_* calls in order (Stream 발행 검증용)."""

    def __init__(self) -> None:
        self.calls: list[tuple] = []
        self.closed = 0

    def start(self) -> None:
        pass

    def publish_session_started(self, connected_at) -> None:
        self.calls.append(("SESSION_STARTED",))

    def publish_child_turn_finalized(self, *, turn_id, turn_seq, text, stt_confidence,
                                     started_at, completed_at, topic_type) -> None:
        self.calls.append(("CHILD_TURN_FINALIZED", turn_id, turn_seq, text,
                           (completed_at - started_at).total_seconds(), topic_type))

    def publish_ai_turn_generated(self, *, turn_id, turn_seq, response_id, text,
                                  question_text, target_slot, topic_type, topic_slots,
                                  skipped_targets, slots_topic_type, topic_question_count,
                                  session_question_count, topic_completion_reason) -> None:
        self.calls.append(("AI_TURN_GENERATED", turn_id, turn_seq, response_id,
                           target_slot, topic_type, topic_slots.event, question_text,
                           slots_topic_type, topic_question_count,
                           session_question_count, topic_completion_reason,
                           skipped_targets))

    def publish_ai_turn_completed(self, *, turn_id, turn_seq, response_id, text,
                                  audio_started_at, audio_completed_at) -> None:
        self.calls.append(("AI_TURN_COMPLETED", turn_id, response_id))

    def publish_session_completed(self, *, reason, completed_at) -> None:
        self.calls.append(("SESSION_COMPLETED", reason))

    async def aclose(self) -> None:
        self.closed += 1


def _build_manager_with_stream(pipeline=None, snapshot=_TWO_TOPIC_SNAPSHOT):
    manager, state, channel, pipeline, registry, sequencer = _build_manager(
        pipeline, snapshot
    )
    stream = _RecordingStreamProducer()
    manager._stream = stream  # noqa: SLF001
    manager._clock = lambda: datetime(2026, 7, 28, 10, 0, 5, tzinfo=timezone.utc)  # noqa: SLF001
    return manager, state, channel, stream, sequencer


class TestStreamPublishing:
    @pytest.mark.asyncio
    async def test_child_and_ai_turns_publish_in_order(self) -> None:
        manager, _, _, stream, sequencer = _build_manager_with_stream(
            _FakeDialoguePipeline([_continue_response()])
        )

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        types = [c[0] for c in stream.calls]
        assert types == ["CHILD_TURN_FINALIZED", "AI_TURN_GENERATED"]

    @pytest.mark.asyncio
    async def test_child_turn_finalized_carries_approx_started_at(self) -> None:
        manager, _, _, stream, sequencer = _build_manager_with_stream()

        await manager.handle_child_turn(
            _child_turn(sequencer),
            SttResult(text="응.", audio_seconds=2.0, avg_logprob=-0.3, no_speech_prob=0.0),
        )

        child = next(c for c in stream.calls if c[0] == "CHILD_TURN_FINALIZED")
        # startedAt = completedAt - audio_seconds (§11-E)
        assert child[4] == pytest.approx(2.0)

    @pytest.mark.asyncio
    async def test_ai_turn_generated_carries_target_and_topic_slots(self) -> None:
        manager, _, _, stream, sequencer = _build_manager_with_stream(
            _FakeDialoguePipeline([_continue_response()])
        )

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        ai = next(c for c in stream.calls if c[0] == "AI_TURN_GENERATED")
        # target은 갱신 슬롯으로 재계산된다(event 채워짐 → 다음 목표는 emotion 계열)
        assert ai[4] is not None
        assert ai[5] == "GAME"  # topic_type
        assert ai[6] == "과일 게임"  # topic_slots.event

    @pytest.mark.asyncio
    async def test_switch_topic_publishes_opening_event_target(self) -> None:
        manager, _, _, stream, sequencer = _build_manager_with_stream(
            _FakeDialoguePipeline([_switch_response()])
        )

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        ai = next(c for c in stream.calls if c[0] == "AI_TURN_GENERATED")
        assert ai[4] == "event"
        assert ai[5] == "DAILY"
        assert ai[8] == "GAME"
        assert ai[9] == 1
        assert ai[10] == 1
        assert ai[11] == "REQUIRED_SLOTS_FILLED"

    @pytest.mark.asyncio
    async def test_terminate_publishes_session_completed(self) -> None:
        manager, _, _, stream, sequencer = _build_manager_with_stream(
            _FakeDialoguePipeline([_terminate_response()])
        )

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert ("SESSION_COMPLETED", "MAX_QUESTION_REACHED") in stream.calls
        assert not any(c[0] == "AI_TURN_GENERATED" for c in stream.calls)

    @pytest.mark.asyncio
    async def test_discarded_response_is_not_streamed(self) -> None:
        """취소·낮은 epoch로 발행이 막힌 턴은 Stream에도 없다(확정만 발행)."""

        manager, _, _, stream, sequencer = _build_manager_with_stream()
        manager._is_epoch_current = lambda: False  # noqa: SLF001

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        # CHILD는 확정됐으니 발행되지만 AI_TURN_GENERATED는 발행 안 됨
        assert any(c[0] == "CHILD_TURN_FINALIZED" for c in stream.calls)
        assert not any(c[0] == "AI_TURN_GENERATED" for c in stream.calls)

    def test_no_speech_terminate_publishes_session_completed(self) -> None:
        manager, _, _, stream, _ = _build_manager_with_stream()

        for _ in range(5):
            manager.handle_no_speech()

        assert any(c[0] == "SESSION_COMPLETED" for c in stream.calls)

    @pytest.mark.asyncio
    async def test_low_confidence_turn_is_not_streamed(self) -> None:
        """되묻기로 폐기된 저신뢰 전사는 확정 이벤트로 발행되지 않는다."""

        manager, _, _, stream, sequencer = _build_manager_with_stream()

        await manager.handle_child_turn(_child_turn(sequencer), _low_confidence_result())

        assert not any(c[0] == "CHILD_TURN_FINALIZED" for c in stream.calls)
        # 되묻기 안내 AI 턴은 정상 발행된다.
        assert any(c[0] == "AI_TURN_GENERATED" for c in stream.calls)

    @pytest.mark.asyncio
    async def test_low_confidence_turn_past_the_limit_is_streamed(self) -> None:
        """되묻기 상한 도달로 대화에 실제 사용된 턴은 발행된다."""

        manager, _, _, stream, sequencer = _build_manager_with_stream()

        await manager.handle_child_turn(_child_turn(sequencer), _low_confidence_result())
        await manager.handle_child_turn(_child_turn(sequencer), _low_confidence_result())

        assert sum(1 for c in stream.calls if c[0] == "CHILD_TURN_FINALIZED") == 1

    @pytest.mark.asyncio
    async def test_goodbye_is_not_streamed_as_a_conversation_turn(self) -> None:
        """마무리 인사는 자막·음성용이며 대화 기록으로 확정 발행하지 않는다."""

        manager, _, _, stream, sequencer = _build_manager_with_stream(
            _FakeDialoguePipeline([_terminate_response()])
        )

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        types = [call[0] for call in stream.calls]
        assert types == ["CHILD_TURN_FINALIZED", "SESSION_COMPLETED"]

    def test_no_speech_with_all_slots_filled_completes_the_session(self) -> None:
        """물을 목표가 없는 무응답도 종료 이벤트를 발행해 세션을 방치하지 않는다."""

        manager, state, _, stream, _ = _build_manager_with_stream()
        state.current_topic.slots = DiarySlots(
            event="과일 게임",
            emotion="즐거움",
            emotion_reason="이겨서",
            memorable_moment="마지막 판",
        )

        manager.handle_no_speech()

        assert ("SESSION_COMPLETED", "REQUIRED_SLOTS_FILLED") in stream.calls
        assert state.terminated is True

    def test_no_speech_with_all_remaining_targets_skipped_times_out(self) -> None:
        manager, state, _, stream, _ = _build_manager_with_stream()
        state.current_topic.slots = DiarySlots(event="과일 게임")
        state.current_topic.skipped_targets = frozenset(
            {
                QuestionTarget.EMOTION,
                QuestionTarget.EMOTION_REASON,
                QuestionTarget.MEMORABLE_MOMENT,
            }
        )

        manager.handle_no_speech()

        assert ("SESSION_COMPLETED", "INACTIVITY_TIMEOUT") in stream.calls
        assert state.terminated is True

    @pytest.mark.asyncio
    async def test_emit_ai_turn_completed_forwards_to_stream(self) -> None:
        manager, _, _, stream, _ = _build_manager_with_stream()
        now = datetime(2026, 7, 28, 10, 0, tzinfo=timezone.utc)

        manager.emit_ai_turn_completed("turn_3", 3, "resp_3", "질문", now, now)

        assert ("AI_TURN_COMPLETED", "turn_3", "resp_3") in stream.calls

    @pytest.mark.asyncio
    async def test_no_stream_producer_keeps_working(self) -> None:
        """producer 미주입 시 회귀 — 이벤트만 없고 대화는 그대로."""

        manager, _, channel, _, _, sequencer = _build_manager(
            _FakeDialoguePipeline([_continue_response()])
        )

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert [event["type"] for event in channel.sent] == [
            "assistant.response.created",
            "assistant.text.final",
            "conversation.slots.updated",
        ]
