"""Session STT pipeline: Frames in, one confirmed turn out (T02b–T05).

Everything is a Fake — no real Provider, no network (§10). Audio is synthetic.
"""

import asyncio
import logging
import math
import struct
from datetime import datetime, timezone
from fractions import Fraction

import pytest
from av import AudioFrame

from app.events.outbound import ConversationEventPublisher
from app.speech.hallucination import HallucinationGuard
from app.speech.recognizer import (
    FakeSpeechRecognizer,
    SttError,
    SttFailureCode,
    SttResult,
)
from app.speech.stt_pipeline import SessionSttPipeline
from app.speech.turn_state import TurnSequencer
from app.speech.vad import FakeVoiceActivityDetector
from app.webrtc.data_channel import FakeDataChannel

_LOGGER = logging.getLogger("test.speech.stt_pipeline")
_WEBRTC_RATE = 48000
_FRAME_SAMPLES = 960  # 20ms


def _frame(tone_hz: int | None = None) -> AudioFrame:
    frame = AudioFrame(format="s16", layout="stereo", samples=_FRAME_SAMPLES)
    frame.sample_rate = _WEBRTC_RATE
    frame.time_base = Fraction(1, _WEBRTC_RATE)
    if tone_hz is None:
        payload = bytes(_FRAME_SAMPLES * 4)
    else:
        payload = b"".join(
            struct.pack(
                "<h", int(10000 * math.sin(2 * math.pi * tone_hz * i / _WEBRTC_RATE))
            )
            for i in range(_FRAME_SAMPLES)
            for _ in range(2)
        )
    for plane in frame.planes:
        plane.update(payload + bytes(plane.buffer_size - len(payload)))
    return frame


def _good_result(seconds: float = 2.0) -> SttResult:
    return SttResult(
        text="오늘 과일 게임 했어.",
        audio_seconds=seconds,
        no_speech_prob=0.0,
        avg_logprob=-0.299,
        reported_end_seconds=seconds,
    )


def _build(
    recognizer: FakeSpeechRecognizer | None = None,
    **kwargs,
) -> tuple[SessionSttPipeline, FakeDataChannel, FakeSpeechRecognizer]:
    channel = FakeDataChannel()
    ids = iter(f"evt_{index}" for index in range(1, 500))
    publisher = ConversationEventPublisher(
        channel,
        conversation_id="conv_1",
        realtime_session_id="rt_1",
        session_epoch=1,
        logger=_LOGGER,
        clock=lambda: datetime(2026, 7, 27, tzinfo=timezone.utc),
        event_id_factory=lambda: next(ids),
    )
    turn_ids = iter(f"turn_{index}" for index in range(1, 500))
    recognizer = recognizer or FakeSpeechRecognizer()
    kwargs.setdefault("turn_sequencer", TurnSequencer(turn_id_factory=lambda: next(turn_ids)))
    pipeline = SessionSttPipeline(recognizer, publisher, _LOGGER, **kwargs)
    return pipeline, channel, recognizer


def _feed(pipeline: SessionSttPipeline, seconds: float, *, tone_hz: int | None = None) -> None:
    for _ in range(int(seconds / 0.02)):
        pipeline.on_frame(_frame(tone_hz))


class TestHappyPath:
    @pytest.mark.asyncio
    async def test_buffered_audio_becomes_one_stt_final(self) -> None:
        recognizer = FakeSpeechRecognizer([_good_result()])
        pipeline, channel, _ = _build(recognizer)
        _feed(pipeline, 2.0)

        result = await pipeline.finalize_turn()

        assert result is not None
        assert [event["type"] for event in channel.sent] == ["stt.final"]
        assert channel.sent[0]["payload"]["text"] == "오늘 과일 게임 했어."
        assert channel.sent[0]["turnId"] == "turn_1"
        assert channel.sent[0]["turnSeq"] == 1

    @pytest.mark.asyncio
    async def test_confidence_is_published_from_avg_logprob(self) -> None:
        pipeline, channel, _ = _build(FakeSpeechRecognizer([_good_result()]))
        _feed(pipeline, 2.0)

        await pipeline.finalize_turn()

        assert channel.sent[0]["payload"]["confidence"] == pytest.approx(0.7415, abs=1e-3)

    @pytest.mark.asyncio
    async def test_provider_receives_a_wav_of_the_buffered_audio(self) -> None:
        pipeline, _, recognizer = _build(FakeSpeechRecognizer([_good_result()]))
        _feed(pipeline, 2.0)

        await pipeline.finalize_turn()

        wav_size, audio_seconds = recognizer.calls[0]
        assert audio_seconds == pytest.approx(2.0, abs=0.05)
        assert wav_size > 44  # RIFF header plus payload

    @pytest.mark.asyncio
    async def test_consecutive_turns_increment_the_sequence(self) -> None:
        recognizer = FakeSpeechRecognizer([_good_result(), _good_result()])
        pipeline, channel, _ = _build(recognizer)

        _feed(pipeline, 2.0)
        await pipeline.finalize_turn()
        _feed(pipeline, 2.0)
        await pipeline.finalize_turn()

        assert [event["turnSeq"] for event in channel.sent] == [1, 2]
        assert [event["turnId"] for event in channel.sent] == ["turn_1", "turn_2"]

    @pytest.mark.asyncio
    async def test_on_final_callback_receives_the_confirmed_turn(self) -> None:
        seen: list[tuple[str, str]] = []

        async def on_final(turn, result) -> None:
            seen.append((turn.turn_id, result.text))

        pipeline, _, _ = _build(FakeSpeechRecognizer([_good_result()]), on_final=on_final)
        _feed(pipeline, 2.0)

        await pipeline.finalize_turn()

        assert seen == [("turn_1", "오늘 과일 게임 했어.")]


class TestIdempotence:
    @pytest.mark.asyncio
    async def test_finalizing_twice_produces_one_final_only(self) -> None:
        """§16: the same turn must never start two questions."""

        recognizer = FakeSpeechRecognizer([_good_result()])
        pipeline, channel, _ = _build(recognizer)
        _feed(pipeline, 2.0)

        first = await pipeline.finalize_turn()
        second = await pipeline.finalize_turn()

        assert first is not None
        assert second is None
        assert len(channel.sent) == 1
        assert len(recognizer.calls) == 1

    @pytest.mark.asyncio
    async def test_finalizing_with_no_audio_does_nothing(self) -> None:
        pipeline, channel, recognizer = _build()

        assert await pipeline.finalize_turn() is None
        assert channel.sent == []
        assert recognizer.calls == []

    @pytest.mark.asyncio
    async def test_concurrent_finalisation_confirms_the_turn_once(self) -> None:
        recognizer = FakeSpeechRecognizer([_good_result(), _good_result()])
        pipeline, channel, _ = _build(recognizer)
        _feed(pipeline, 2.0)

        await asyncio.gather(pipeline.finalize_turn(), pipeline.finalize_turn())

        assert len(channel.sent) == 1
        # The buffer is handed over before the await, so the losing caller finds
        # nothing to recognise — one Provider call, not two deduped ones.
        assert len(recognizer.calls) == 1


class _GatedRecognizer:
    """Blocks inside transcribe() until released — models the ~2s whisper call."""

    def __init__(self, result: SttResult) -> None:
        self._result = result
        self.entered = asyncio.Event()
        self.release = asyncio.Event()
        self.calls: list[tuple[int, float]] = []

    async def transcribe(self, wav_bytes: bytes, audio_seconds: float) -> SttResult:
        self.calls.append((len(wav_bytes), audio_seconds))
        self.entered.set()
        await self.release.wait()
        return self._result


class TestAudioDuringRecognition:
    @pytest.mark.asyncio
    async def test_frames_arriving_during_recognition_survive_into_the_next_turn(
        self,
    ) -> None:
        """Recognition takes ~2s; the child may already be speaking again.

        Regression for a review finding: the buffer used to be reset *after*
        the Provider await, wiping every frame that arrived in that window —
        real audio loss on the overflow path and for fast back-to-back
        utterances once W02 wires VAD in.
        """

        recognizer = _GatedRecognizer(_good_result())
        pipeline, channel, _ = _build()
        pipeline._recognizer = recognizer  # noqa: SLF001 - gate the await window
        _feed(pipeline, 2.0)

        finalize = asyncio.create_task(pipeline.finalize_turn())
        await asyncio.wait_for(recognizer.entered.wait(), timeout=1)

        _feed(pipeline, 1.0)  # the child's next words, mid-recognition

        recognizer.release.set()
        await finalize

        assert pipeline.has_audio  # next utterance kept, not wiped
        assert pipeline.current_turn is not None
        assert pipeline.current_turn.turn_seq == 2
        assert channel.sent[-1]["type"] == "stt.final"

    @pytest.mark.asyncio
    async def test_hallucination_rejection_does_not_wipe_the_next_utterance(
        self,
    ) -> None:
        hallucination = SttResult(
            text="you", audio_seconds=2.0, no_speech_prob=0.94, avg_logprob=-0.5
        )
        recognizer = _GatedRecognizer(hallucination)
        pipeline, channel, _ = _build()
        pipeline._recognizer = recognizer  # noqa: SLF001
        _feed(pipeline, 2.0)

        finalize = asyncio.create_task(pipeline.finalize_turn())
        await asyncio.wait_for(recognizer.entered.wait(), timeout=1)
        _feed(pipeline, 1.0)
        recognizer.release.set()
        await finalize

        assert channel.sent == []  # rejected, no final
        assert pipeline.has_audio  # but the next utterance is intact


class TestRejectedAudio:
    @pytest.mark.asyncio
    async def test_audio_below_the_floor_is_never_sent_to_the_provider(self) -> None:
        pipeline, channel, recognizer = _build()
        _feed(pipeline, 0.2)

        assert await pipeline.finalize_turn() is None
        assert recognizer.calls == []
        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_hallucinated_silence_does_not_become_a_turn(self) -> None:
        """The whole point of the guard: no "you"/"감사합니다." in the diary."""

        hallucination = SttResult(
            text="you", audio_seconds=2.0, no_speech_prob=0.94, avg_logprob=-0.515
        )
        pipeline, channel, _ = _build(FakeSpeechRecognizer([hallucination]))
        _feed(pipeline, 2.0)

        assert await pipeline.finalize_turn() is None
        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_rejected_audio_is_not_reported_as_an_error(self) -> None:
        """Silence means the child did not speak — recovery is W03's policy,
        not an error event (ADR-018)."""

        hallucination = SttResult(
            text="감사합니다.", audio_seconds=2.0, no_speech_prob=0.94, avg_logprob=-0.5
        )
        pipeline, channel, _ = _build(FakeSpeechRecognizer([hallucination]))
        _feed(pipeline, 2.0)

        await pipeline.finalize_turn()

        assert [event["type"] for event in channel.sent] == []

    @pytest.mark.asyncio
    async def test_a_rejected_turn_does_not_block_the_next_one(self) -> None:
        hallucination = SttResult(
            text="you", audio_seconds=2.0, no_speech_prob=0.94, avg_logprob=-0.5
        )
        recognizer = FakeSpeechRecognizer([hallucination, _good_result()])
        pipeline, channel, _ = _build(recognizer)

        _feed(pipeline, 2.0)
        await pipeline.finalize_turn()
        _feed(pipeline, 2.0)
        await pipeline.finalize_turn()

        assert [event["type"] for event in channel.sent] == ["stt.final"]


class TestProviderFailure:
    @pytest.mark.asyncio
    async def test_timeout_emits_a_recoverable_error_event(self) -> None:
        recognizer = FakeSpeechRecognizer([SttError(SttFailureCode.PROVIDER_TIMEOUT)])
        pipeline, channel, _ = _build(recognizer)
        _feed(pipeline, 2.0)

        assert await pipeline.finalize_turn() is None

        assert channel.sent[0]["type"] == "error"
        assert channel.sent[0]["payload"]["code"] == "STT_FAILED"
        assert channel.sent[0]["payload"]["recoverable"] is True

    @pytest.mark.asyncio
    async def test_failure_does_not_emit_a_final(self) -> None:
        recognizer = FakeSpeechRecognizer([SttError(SttFailureCode.PROVIDER_FAILED)])
        pipeline, channel, _ = _build(recognizer)
        _feed(pipeline, 2.0)

        await pipeline.finalize_turn()

        assert all(event["type"] != "stt.final" for event in channel.sent)

    @pytest.mark.asyncio
    async def test_provider_is_called_once_per_turn_without_retry(self) -> None:
        recognizer = FakeSpeechRecognizer([SttError(SttFailureCode.PROVIDER_FAILED)])
        pipeline, _, _ = _build(recognizer)
        _feed(pipeline, 2.0)

        await pipeline.finalize_turn()

        assert len(recognizer.calls) == 1

    @pytest.mark.asyncio
    async def test_conversation_continues_after_a_failed_turn(self) -> None:
        recognizer = FakeSpeechRecognizer(
            [SttError(SttFailureCode.PROVIDER_FAILED), _good_result()]
        )
        pipeline, channel, _ = _build(recognizer)

        _feed(pipeline, 2.0)
        await pipeline.finalize_turn()
        _feed(pipeline, 2.0)
        await pipeline.finalize_turn()

        assert [event["type"] for event in channel.sent] == ["error", "stt.final"]
        assert channel.sent[1]["turnSeq"] == 2


class TestOverflow:
    @pytest.mark.asyncio
    async def test_reaching_the_cap_finalises_the_turn_automatically(self) -> None:
        recognizer = FakeSpeechRecognizer([_good_result(1.0)])
        pipeline, channel, _ = _build(recognizer, max_utterance_seconds=1.0)

        _feed(pipeline, 1.2)
        await asyncio.sleep(0)  # let the scheduled finalisation run
        await asyncio.sleep(0)

        assert [event["type"] for event in channel.sent] == ["stt.final"]

    @pytest.mark.asyncio
    async def test_overflow_keeps_the_audio_it_already_had(self) -> None:
        """§3-3: a batch input is never spliced."""

        recognizer = FakeSpeechRecognizer([_good_result(1.0)])
        pipeline, _, _ = _build(recognizer, max_utterance_seconds=1.0)

        _feed(pipeline, 1.2)
        for _ in range(4):
            await asyncio.sleep(0)

        _, audio_seconds = recognizer.calls[0]
        assert audio_seconds >= 1.0


class TestLifecycle:
    @pytest.mark.asyncio
    async def test_aclose_is_idempotent(self) -> None:
        pipeline, _, _ = _build()
        _feed(pipeline, 0.5)

        await pipeline.aclose()
        await pipeline.aclose()

        assert not pipeline.has_audio

    @pytest.mark.asyncio
    async def test_frames_after_close_are_ignored(self) -> None:
        pipeline, _, _ = _build()
        await pipeline.aclose()

        pipeline.on_frame(_frame())

        assert not pipeline.has_audio

    @pytest.mark.asyncio
    async def test_finalize_after_close_does_nothing(self) -> None:
        pipeline, channel, recognizer = _build()
        _feed(pipeline, 2.0)
        await pipeline.aclose()

        assert await pipeline.finalize_turn() is None
        assert recognizer.calls == []
        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_aclose_cancels_an_in_flight_recognition(self) -> None:
        started = asyncio.Event()

        class _HangingRecognizer:
            calls: list = []

            async def transcribe(self, wav_bytes: bytes, audio_seconds: float) -> SttResult:
                started.set()
                await asyncio.sleep(3600)
                raise AssertionError("should have been cancelled")

        pipeline, channel, _ = _build(max_utterance_seconds=1.0)
        pipeline._recognizer = _HangingRecognizer()  # noqa: SLF001 - drive the hang path
        _feed(pipeline, 1.2)
        await asyncio.wait_for(started.wait(), timeout=1)

        await asyncio.wait_for(pipeline.aclose(), timeout=1)

        assert channel.sent == []


class TestIsolation:
    @pytest.mark.asyncio
    async def test_two_pipelines_do_not_share_audio_or_turn_numbers(self) -> None:
        first, first_channel, _ = _build(FakeSpeechRecognizer([_good_result()]))
        second, second_channel, second_recognizer = _build()

        _feed(first, 2.0)
        await first.finalize_turn()

        assert not second.has_audio
        assert second_recognizer.calls == []
        assert second_channel.sent == []
        assert first_channel.sent[0]["turnSeq"] == 1

    @pytest.mark.asyncio
    async def test_guard_thresholds_are_taken_from_the_injected_guard(self) -> None:
        strict = HallucinationGuard(min_audio_seconds=5.0)
        pipeline, channel, recognizer = _build(guard=strict)
        _feed(pipeline, 2.0)

        assert await pipeline.finalize_turn() is None
        assert recognizer.calls == []
        assert channel.sent == []


class TestOutputGate:
    """ADR-020: AI가 말하는 동안 입력을 받지 않는다 — Echo 방어를 서버가 보장한다."""

    def _build_gated(self, speaking: list[bool], recognizer=None, **kwargs):
        kwargs.setdefault("vad", FakeVoiceActivityDetector())
        kwargs["is_output_active"] = lambda: speaking[0]
        return _build(recognizer, **kwargs)

    @pytest.mark.asyncio
    async def test_audio_during_assistant_speech_is_not_buffered(self) -> None:
        """AI 음성이 마이크로 되돌아와도 아이 발화로 전사되지 않는다."""

        speaking = [True]
        pipeline, channel, recognizer = self._build_gated(speaking)

        _feed(pipeline, 2.0, tone_hz=440)  # Echo로 되돌아온 AI 음성

        assert not pipeline.has_audio
        assert recognizer.calls == []
        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_input_resumes_after_the_assistant_stops(self) -> None:
        recognizer = FakeSpeechRecognizer([_good_result()])
        speaking = [True]
        pipeline, channel, _ = self._build_gated(speaking, recognizer)

        _feed(pipeline, 1.0, tone_hz=440)   # 게이트 닫힘 — 무시
        speaking[0] = False
        _feed(pipeline, 1.0, tone_hz=440)   # 아이 발화
        _feed(pipeline, 1.2)                # 종료 판정 침묵
        await asyncio.sleep(0)
        await asyncio.sleep(0)

        assert [event["type"] for event in channel.sent] == ["stt.final"]

    @pytest.mark.asyncio
    async def test_a_half_spoken_utterance_is_discarded_when_the_gate_closes(self) -> None:
        """게이트가 닫힐 때 반쪽 발화를 버린다.

        AI 응답 생성(whisper+LLM, 실측 3초 이상) 중에도 듣고 있으므로 재생 시작
        시점에 아이가 말하던 중일 수 있다. 남겨 두면 게이트가 열린 뒤 도착하는
        뒷부분과 이어붙어 깨진 문장이 된다(ADR-020이 받아들인 유실).
        """

        speaking = [False]
        pipeline, _, recognizer = self._build_gated(speaking)
        _feed(pipeline, 0.6, tone_hz=440)
        assert pipeline.has_audio  # 발화가 버퍼에 쌓였다

        speaking[0] = True
        _feed(pipeline, 0.1, tone_hz=440)  # 게이트 닫힘을 감지하는 첫 프레임

        assert not pipeline.has_audio
        assert pipeline.current_turn is None
        assert recognizer.calls == []

    @pytest.mark.asyncio
    async def test_no_gate_keeps_the_previous_behaviour(self) -> None:
        """게이트 미주입(오디오 없는 연결)은 기존대로 전부 받는다."""

        pipeline, _, _ = _build(vad=FakeVoiceActivityDetector())

        _feed(pipeline, 1.0, tone_hz=440)

        assert pipeline.has_audio


class TestTurnDetectorIntegration:
    """T08: the pipeline with a VAD gates audio and self-finalizes on silence."""

    def _build_with_vad(self, recognizer=None, **kwargs):
        kwargs.setdefault("vad", FakeVoiceActivityDetector())
        return _build(recognizer, **kwargs)

    @pytest.mark.asyncio
    async def test_leading_silence_never_reaches_the_buffer(self) -> None:
        """§11-C gating: what came before speech is not buffered, not billed,
        and cannot hallucinate."""

        pipeline, channel, recognizer = self._build_with_vad()

        _feed(pipeline, 2.0)  # digital silence

        assert not pipeline.has_audio
        assert recognizer.calls == []
        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_speech_confirmation_starts_buffering_with_preroll(self) -> None:
        pipeline, _, _ = self._build_with_vad()

        _feed(pipeline, 1.0)                 # leading silence — dropped
        _feed(pipeline, 0.4, tone_hz=440)    # confirmed speech

        assert pipeline.has_audio
        # pre-roll(300ms 상한)이 포함되되 선행 침묵 1초 전체가 들어오진 않는다
        assert 0.4 <= pipeline._buffer.duration_seconds <= 1.0  # noqa: SLF001

    @pytest.mark.asyncio
    async def test_trailing_silence_finalizes_the_turn_automatically(self) -> None:
        """The full W02 loop: speech -> silence -> stt.final without anyone
        calling finalize_turn() from outside."""

        recognizer = FakeSpeechRecognizer([_good_result()])
        pipeline, channel, _ = self._build_with_vad(recognizer)

        _feed(pipeline, 1.0, tone_hz=440)   # 발화
        _feed(pipeline, 1.2)                 # 침묵 1.0초 초과 → on_turn_end
        for _ in range(4):
            await asyncio.sleep(0)           # 예약된 finalize task 실행

        assert [event["type"] for event in channel.sent] == ["stt.final"]
        assert len(recognizer.calls) == 1

    @pytest.mark.asyncio
    async def test_a_mid_utterance_pause_does_not_split_the_turn(self) -> None:
        """CONV-REQ-072 end to end: 0.9s pause then more speech = one turn."""

        recognizer = FakeSpeechRecognizer([_good_result()])
        pipeline, channel, _ = self._build_with_vad(recognizer)

        _feed(pipeline, 0.5, tone_hz=440)
        _feed(pipeline, 0.9)                 # 임계값 직전 멈춤
        _feed(pipeline, 0.5, tone_hz=440)
        _feed(pipeline, 1.2)                 # 진짜 종료
        for _ in range(4):
            await asyncio.sleep(0)

        assert len(recognizer.calls) == 1    # 한 턴, 한 번의 인식
        duration = recognizer.calls[0][1]
        assert duration >= 1.5               # 멈춤 구간까지 한 발화로 포함됐다

    @pytest.mark.asyncio
    async def test_two_utterances_become_two_turns(self) -> None:
        recognizer = FakeSpeechRecognizer([_good_result(), _good_result()])
        pipeline, channel, _ = self._build_with_vad(recognizer)

        _feed(pipeline, 0.6, tone_hz=440)
        _feed(pipeline, 1.2)
        for _ in range(4):
            await asyncio.sleep(0)
        _feed(pipeline, 0.6, tone_hz=440)
        _feed(pipeline, 1.2)
        for _ in range(4):
            await asyncio.sleep(0)

        assert [event["turnSeq"] for event in channel.sent] == [1, 2]

    @pytest.mark.asyncio
    async def test_a_cough_produces_no_turn_and_no_provider_call(self) -> None:
        pipeline, channel, recognizer = self._build_with_vad()

        _feed(pipeline, 0.12, tone_hz=440)   # ~120ms burst
        _feed(pipeline, 5.0)                 # 이후 긴 침묵

        for _ in range(4):
            await asyncio.sleep(0)
        assert recognizer.calls == []
        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_no_speech_callback_reaches_the_injected_handler(self) -> None:
        signals: list[int] = []
        pipeline, _, _ = self._build_with_vad(on_no_speech=lambda: signals.append(1))

        _feed(pipeline, 10.2)                # 무응답 timeout(10초) 초과

        assert signals == [1]

    @pytest.mark.asyncio
    async def test_request_finalize_is_cancellable_via_aclose(self) -> None:
        """W01 리뷰 후속의 요지: 외부 요청 경로도 aclose()가 취소할 수 있다."""

        started = asyncio.Event()

        class _HangingRecognizer:
            async def transcribe(self, wav_bytes: bytes, audio_seconds: float) -> SttResult:
                started.set()
                await asyncio.sleep(3600)
                raise AssertionError("should have been cancelled")

        pipeline, channel, _ = self._build_with_vad()
        pipeline._recognizer = _HangingRecognizer()  # noqa: SLF001
        _feed(pipeline, 1.0, tone_hz=440)

        pipeline.request_finalize()
        await asyncio.wait_for(started.wait(), timeout=1)
        await asyncio.wait_for(pipeline.aclose(), timeout=1)

        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_request_finalize_after_close_is_a_no_op(self) -> None:
        pipeline, channel, recognizer = self._build_with_vad()
        _feed(pipeline, 1.0, tone_hz=440)
        await pipeline.aclose()

        pipeline.request_finalize()
        for _ in range(4):
            await asyncio.sleep(0)

        assert recognizer.calls == []
        assert channel.sent == []

    @pytest.mark.asyncio
    async def test_aclose_resets_the_detector_state(self) -> None:
        pipeline, _, _ = self._build_with_vad()
        _feed(pipeline, 1.0, tone_hz=440)    # SPEAKING 상태로 만든 뒤

        await pipeline.aclose()

        assert pipeline._detector.phase.value == "idle"  # noqa: SLF001


class TestPendingFinalize:
    """W03 리뷰 발견: on_final(LLM ~1-3초)이 finalize task를 길게 만들면서,
    그 동안 끝난 다음 발화의 finalize 요청이 조용히 skip되어 턴이 유실됐다."""

    @pytest.mark.asyncio
    async def test_finalize_requested_during_a_long_on_final_is_not_lost(self) -> None:
        release = asyncio.Event()
        entered = asyncio.Event()
        finals: list[str] = []

        async def slow_on_final(turn, result) -> None:
            finals.append(result.text)
            if len(finals) == 1:
                entered.set()
                await release.wait()  # LLM 호출을 흉내내는 긴 대기

        first = SttResult(text="첫 발화.", audio_seconds=2.0, avg_logprob=-0.3,
                          no_speech_prob=0.0, reported_end_seconds=2.0)
        second = SttResult(text="둘째 발화.", audio_seconds=2.0, avg_logprob=-0.3,
                           no_speech_prob=0.0, reported_end_seconds=2.0)
        pipeline, channel, _ = _build(
            FakeSpeechRecognizer([first, second]), on_final=slow_on_final
        )

        _feed(pipeline, 2.0)
        pipeline.request_finalize()
        await asyncio.wait_for(entered.wait(), timeout=1)

        # 첫 턴의 dialogue가 진행되는 동안 아이가 다음 발화를 끝냈다
        _feed(pipeline, 2.0)
        pipeline.request_finalize()

        release.set()
        for _ in range(8):
            await asyncio.sleep(0)

        assert finals == ["첫 발화.", "둘째 발화."]  # 둘째 턴이 유실되지 않는다
        assert [event["turnSeq"] for event in channel.sent] == [1, 2]

    @pytest.mark.asyncio
    async def test_pending_finalize_is_dropped_on_close(self) -> None:
        release = asyncio.Event()
        entered = asyncio.Event()

        async def slow_on_final(turn, result) -> None:
            entered.set()
            await release.wait()

        pipeline, channel, recognizer = _build(
            FakeSpeechRecognizer([_good_result(), _good_result()]),
            on_final=slow_on_final,
        )
        _feed(pipeline, 2.0)
        pipeline.request_finalize()
        await asyncio.wait_for(entered.wait(), timeout=1)
        _feed(pipeline, 2.0)
        pipeline.request_finalize()  # pending으로 대기

        release.set()
        await asyncio.wait_for(pipeline.aclose(), timeout=1)

        assert len(recognizer.calls) <= 1  # 닫힌 뒤 pending이 실행되지 않는다
