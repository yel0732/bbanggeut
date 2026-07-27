"""Session STT pipeline: Frames in, one confirmed turn out (T02b–T05).

Everything is a Fake — no real Provider, no network (§10). Audio is synthetic.
"""

import asyncio
import logging
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
from app.webrtc.data_channel import FakeDataChannel

_LOGGER = logging.getLogger("test.speech.stt_pipeline")
_WEBRTC_RATE = 48000
_FRAME_SAMPLES = 960  # 20ms


def _frame() -> AudioFrame:
    frame = AudioFrame(format="s16", layout="stereo", samples=_FRAME_SAMPLES)
    frame.sample_rate = _WEBRTC_RATE
    frame.time_base = Fraction(1, _WEBRTC_RATE)
    for plane in frame.planes:
        plane.update(bytes(plane.buffer_size))
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


def _feed(pipeline: SessionSttPipeline, seconds: float) -> None:
    for _ in range(int(seconds / 0.02)):
        pipeline.on_frame(_frame())


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
