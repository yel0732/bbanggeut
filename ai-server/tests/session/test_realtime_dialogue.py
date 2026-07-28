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
        question_generation_status=QuestionGenerationStatus.TEMPLATE_USED,
        question_generation_source=QuestionGenerationSource.RULE_BASED_TEMPLATE,
    )


def _terminate_response() -> DialogueTurnResponse:
    return DialogueTurnResponse(
        updated_slots=DiarySlots(event="과일 게임", emotion="신남"),
        decision=DialogueTurnDecision.TERMINATE_SESSION,
        termination_reason=DialogueTerminationReason.MAX_QUESTION_REACHED,
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


class TestChildTurn:
    @pytest.mark.asyncio
    async def test_publishes_created_then_text_final_with_one_response_id(self) -> None:
        manager, _, channel, _, _, sequencer = _build_manager()

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        assert [event["type"] for event in channel.sent] == [
            "assistant.response.created",
            "assistant.text.final",
        ]
        created, text_final = channel.sent
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
    async def test_terminate_publishes_nothing_and_blocks_further_turns(self) -> None:
        pipeline = _FakeDialoguePipeline([_terminate_response()])
        manager, state, channel, _, _, sequencer = _build_manager(pipeline)

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())
        await manager.handle_child_turn(_child_turn(sequencer), _stt_result("또 하고 싶어."))

        assert state.terminated is True
        assert channel.sent == []
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
        ]
        assert "말해 줄래" in channel.sent[1]["payload"]["text"]
        assert state.consecutive_no_responses == 1

    def test_second_no_speech_asks_to_repeat(self) -> None:
        manager, _, channel, _, _, _ = _build_manager()

        manager.handle_no_speech()
        manager.handle_no_speech()

        assert channel.sent[3]["payload"]["text"] == "괜찮아, 천천히 다시 말해 줄래?"

    def test_fifth_no_speech_terminates_without_publishing(self) -> None:
        manager, state, channel, _, _, _ = _build_manager()

        for _ in range(5):
            manager.handle_no_speech()

        assert state.terminated is True
        # 1~4회차는 각각 2개 이벤트, 5회차(종료)는 발행 없음
        assert len(channel.sent) == 8

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

    def test_no_speech_after_termination_is_ignored(self) -> None:
        manager, state, channel, _, _, _ = _build_manager()
        state.terminated = True

        manager.handle_no_speech()

        assert channel.sent == []


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

        def stt_pipeline_factory(publisher, *, turn_sequencer, on_final, on_no_speech):
            captured.update(
                sequencer=turn_sequencer, on_final=on_final, on_no_speech=on_no_speech
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
                                  slots_topic_type, topic_question_count,
                                  session_question_count, topic_completion_reason) -> None:
        self.calls.append(("AI_TURN_GENERATED", turn_id, turn_seq, response_id,
                           target_slot, topic_type, topic_slots.event, question_text,
                           slots_topic_type, topic_question_count,
                           session_question_count, topic_completion_reason))

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
    async def test_switch_topic_publishes_null_target(self) -> None:
        manager, _, _, stream, sequencer = _build_manager_with_stream(
            _FakeDialoguePipeline([_switch_response()])
        )

        await manager.handle_child_turn(_child_turn(sequencer), _stt_result())

        ai = next(c for c in stream.calls if c[0] == "AI_TURN_GENERATED")
        assert ai[4] is None  # 전환 질문은 목표 없음
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

        assert channel.sent[-1]["type"] == "assistant.text.final"
