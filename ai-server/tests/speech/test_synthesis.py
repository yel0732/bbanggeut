"""Speech synthesis service tests (T19) — Fake synthesizer, no network."""

import asyncio
import logging
from datetime import datetime, timezone

import pytest

from app.events.outbound import ConversationEventPublisher
from app.session.response_registry import ResponseRegistry
from app.speech.audio_format import TTS_OUTPUT_FORMAT
from app.speech.speaker import SpeakerAudioTrack, SpeakerHandle
from app.speech.synthesis import SpeechSynthesisService
from app.speech.tts_client import FakeSpeechSynthesizer, TtsError, TtsFailureCode
from app.webrtc.data_channel import FakeDataChannel

_LOGGER = logging.getLogger("test.speech.synthesis")
# 0.1초 분량(2400 samples * 2 = 4800 bytes) — 여러 20ms 프레임이 나온다.
_CHUNK = b"\x11\x22" * 2400


def _build(synthesizer):
    channel = FakeDataChannel()
    event_ids = iter(f"evt_{i}" for i in range(1, 500))
    publisher = ConversationEventPublisher(
        channel,
        conversation_id="conv_1",
        realtime_session_id="rt_1",
        session_epoch=1,
        logger=_LOGGER,
        clock=lambda: datetime(2026, 7, 28, tzinfo=timezone.utc),
        event_id_factory=lambda: next(event_ids),
    )
    registry = ResponseRegistry(response_id_factory=lambda: "resp_x")
    speaker = SpeakerHandle(SpeakerAudioTrack(), max_queue_frames=500)
    service = SpeechSynthesisService(
        synthesizer, speaker, publisher, registry.is_cancelled, _LOGGER
    )
    return service, channel, registry, speaker


async def _drain_track(speaker_queue) -> None:
    """Consume everything the queue holds so `drained` becomes true."""

    while speaker_queue.get_nowait() is not None:
        await asyncio.sleep(0)


class TestHappyPath:
    @pytest.mark.asyncio
    async def test_started_then_completed(self) -> None:
        synth = FakeSpeechSynthesizer([_CHUNK])
        service, channel, _, speaker = _build(synth)

        service.speak("turn_2", 2, "resp_1", "안녕 오늘 뭐 했어?")

        async def _completed() -> None:
            # 트랙이 프레임을 소비해야 drained → completed. 서비스의 drain 폴링은
            # 실시간 sleep이라 여기서도 실시간으로 소비·대기한다.
            while not any(e["type"] == "assistant.audio.completed" for e in channel.sent):
                queue = speaker._current  # noqa: SLF001
                if queue is not None:
                    while queue.get_nowait() is not None:
                        pass
                await asyncio.sleep(0.01)

        await asyncio.wait_for(_completed(), timeout=2)

        types = [e["type"] for e in channel.sent]
        assert types[0] == "assistant.audio.started"
        assert types[-1] == "assistant.audio.completed"

    @pytest.mark.asyncio
    async def test_started_event_carries_turn_and_response_ids(self) -> None:
        synth = FakeSpeechSynthesizer([_CHUNK])
        service, channel, _, _ = _build(synth)

        service.speak("turn_2", 2, "resp_1", "질문")
        await synth.started.wait()
        for _ in range(5):
            await asyncio.sleep(0)

        started = next(e for e in channel.sent if e["type"] == "assistant.audio.started")
        assert started["turnId"] == "turn_2"
        assert started["turnSeq"] == 2
        assert started["responseId"] == "resp_1"

    @pytest.mark.asyncio
    async def test_completed_waits_until_the_track_consumes_the_audio(self) -> None:
        """§11-C: completed = 송신 완료, not 합성 완료. 큐에 프레임이 남아
        있는 동안에는 completed가 나가면 안 된다."""

        synth = FakeSpeechSynthesizer([_CHUNK])
        service, channel, _, speaker = _build(synth)

        service.speak("turn_2", 2, "resp_1", "질문")
        await synth.started.wait()
        # 합성은 곧 끝나지만 트랙이 큐를 비우지 않았다 — 잠시 기다려도 completed 없음.
        for _ in range(20):
            await asyncio.sleep(0.005)
        assert not any(e["type"] == "assistant.audio.completed" for e in channel.sent)

        # 이제 트랙이 소비하면 completed가 나간다.
        async def _drain_until_completed() -> None:
            while not any(e["type"] == "assistant.audio.completed" for e in channel.sent):
                while speaker._current.get_nowait() is not None:  # noqa: SLF001
                    pass
                await asyncio.sleep(0.01)

        await asyncio.wait_for(_drain_until_completed(), timeout=2)

    @pytest.mark.asyncio
    async def test_first_audio_reaches_the_queue_before_synthesis_finishes(self) -> None:
        """완료 조건 1: 전체 합성 완료 전에 첫 Audio가 나간다."""

        synth = FakeSpeechSynthesizer([_CHUNK, _CHUNK, _CHUNK])
        synth.release = asyncio.Event()  # 2번째 chunk부터 게이트로 막는다
        service, channel, _, speaker = _build(synth)

        service.speak("turn_2", 2, "resp_1", "긴 질문")
        await synth.started.wait()
        for _ in range(10):
            await asyncio.sleep(0)

        # 합성이 첫 chunk에서 멈춰 있는데도 started가 이미 나갔고 오디오가 큐에 있다
        assert any(e["type"] == "assistant.audio.started" for e in channel.sent)
        assert speaker._current.get_nowait() is not None  # noqa: SLF001
        synth.release.set()
        await service.aclose()


class TestFailure:
    @pytest.mark.asyncio
    async def test_provider_failure_emits_failed_and_flushes(self) -> None:
        synth = FakeSpeechSynthesizer([], error=TtsError(TtsFailureCode.PROVIDER_FAILED))
        service, channel, _, speaker = _build(synth)

        service.speak("turn_2", 2, "resp_1", "질문")
        for _ in range(10):
            await asyncio.sleep(0)

        failed = [e for e in channel.sent if e["type"] == "assistant.audio.failed"]
        assert len(failed) == 1
        assert failed[0]["payload"]["code"] == "TTS_FAILED"
        assert not any(e["type"] == "assistant.audio.completed" for e in channel.sent)

    @pytest.mark.asyncio
    async def test_failure_before_any_audio_still_reports(self) -> None:
        synth = FakeSpeechSynthesizer([], error=TtsError(TtsFailureCode.PROVIDER_TIMEOUT))
        service, channel, _, _ = _build(synth)

        service.speak("turn_2", 2, "resp_1", "질문")
        for _ in range(10):
            await asyncio.sleep(0)

        assert [e["type"] for e in channel.sent] == ["assistant.audio.failed"]


class TestCancellation:
    @pytest.mark.asyncio
    async def test_cancel_mid_stream_flushes_and_skips_completed(self) -> None:
        """Barge-in during synthesis: the loop must detect the cancel itself,
        flush, and return — WITHOUT relying on aclose to stop it."""

        synth = FakeSpeechSynthesizer([_CHUNK, _CHUNK, _CHUNK])
        synth.release = asyncio.Event()
        service, channel, registry, speaker = _build(synth)

        registry.issue()  # 매니저가 발급하듯 resp_x를 known으로 만든다
        service.speak("turn_2", 2, "resp_x", "긴 질문")
        await synth.started.wait()
        for _ in range(5):
            await asyncio.sleep(0)
        queue = speaker._current  # noqa: SLF001

        registry.cancel("resp_x")  # barge-in
        synth.release.set()  # let the remaining chunks flow — they must be dropped
        # 태스크가 스스로 취소를 감지하고 끝나기를 기다린다(aclose 없이).
        for _ in range(50):
            await asyncio.sleep(0.005)
            if service._task is not None and service._task.done():  # noqa: SLF001
                break

        assert queue.is_flushed
        assert not any(e["type"] == "assistant.audio.completed" for e in channel.sent)

    @pytest.mark.asyncio
    async def test_speak_is_a_noop_for_an_already_cancelled_response(self) -> None:
        synth = FakeSpeechSynthesizer([_CHUNK])
        service, channel, registry, _ = _build(synth)

        # resp_x가 known이 되도록 발급 후 취소한다(factory가 항상 "resp_x" 반환).
        registry.cancel(registry.issue())

        service.speak("turn_2", 2, "resp_x", "질문")
        for _ in range(5):
            await asyncio.sleep(0)

        assert channel.sent == []
        assert synth.calls == []


class TestLifecycle:
    @pytest.mark.asyncio
    async def test_aclose_cancels_in_flight_synthesis(self) -> None:
        synth = FakeSpeechSynthesizer([_CHUNK, _CHUNK])
        synth.release = asyncio.Event()  # 무한 대기시켜 진행 중 상태로 만든다
        service, channel, _, _ = _build(synth)

        service.speak("turn_2", 2, "resp_1", "질문")
        await synth.started.wait()

        await asyncio.wait_for(service.aclose(), timeout=1)

        assert not any(e["type"] == "assistant.audio.completed" for e in channel.sent)

    @pytest.mark.asyncio
    async def test_aclose_is_idempotent(self) -> None:
        service, _, _, _ = _build(FakeSpeechSynthesizer([_CHUNK]))

        await service.aclose()
        await service.aclose()


class TestEmptyAudio:
    @pytest.mark.asyncio
    async def test_no_audio_produces_neither_started_nor_completed(self) -> None:
        synth = FakeSpeechSynthesizer([])  # 빈 스트림
        service, channel, _, _ = _build(synth)

        service.speak("turn_2", 2, "resp_1", "질문")
        for _ in range(10):
            await asyncio.sleep(0)

        assert channel.sent == []


class TestOnCompleted:
    @pytest.mark.asyncio
    async def test_on_completed_fires_with_wall_times(self) -> None:
        from datetime import datetime, timezone

        seen: list = []
        synth = FakeSpeechSynthesizer([_CHUNK])
        channel = FakeDataChannel()
        publisher = ConversationEventPublisher(
            channel, conversation_id="c", realtime_session_id="r",
            session_epoch=1, logger=_LOGGER)
        registry = ResponseRegistry(response_id_factory=lambda: "resp_x")
        speaker = SpeakerHandle(SpeakerAudioTrack(), max_queue_frames=500)
        service = SpeechSynthesisService(
            synth, speaker, publisher, registry.is_cancelled, _LOGGER,
            on_completed=lambda *a: seen.append(a),
            clock=lambda: datetime(2026, 7, 28, 10, 0, tzinfo=timezone.utc),
        )

        service.speak("turn_2", 2, "resp_1", "질문")
        async def _drain() -> None:
            while not seen:
                q = speaker._current  # noqa: SLF001
                if q is not None:
                    while q.get_nowait() is not None:
                        pass
                await asyncio.sleep(0.01)
        await asyncio.wait_for(_drain(), timeout=2)

        turn_id, turn_seq, response_id, text, started, completed = seen[0]
        assert (turn_id, turn_seq, response_id, text) == ("turn_2", 2, "resp_1", "질문")
        assert started.tzinfo is not None and completed.tzinfo is not None

    @pytest.mark.asyncio
    async def test_on_completed_not_called_on_cancel(self) -> None:
        seen: list = []
        synth = FakeSpeechSynthesizer([_CHUNK, _CHUNK, _CHUNK])
        synth.release = asyncio.Event()
        channel = FakeDataChannel()
        publisher = ConversationEventPublisher(
            channel, conversation_id="c", realtime_session_id="r",
            session_epoch=1, logger=_LOGGER)
        registry = ResponseRegistry(response_id_factory=lambda: "resp_x")
        speaker = SpeakerHandle(SpeakerAudioTrack(), max_queue_frames=500)
        service = SpeechSynthesisService(
            synth, speaker, publisher, registry.is_cancelled, _LOGGER,
            on_completed=lambda *a: seen.append(a))

        registry.issue()
        service.speak("turn_2", 2, "resp_x", "질문")
        await synth.started.wait()
        for _ in range(5):
            await asyncio.sleep(0)
        registry.cancel("resp_x")
        synth.release.set()
        await service.aclose()

        assert seen == []
