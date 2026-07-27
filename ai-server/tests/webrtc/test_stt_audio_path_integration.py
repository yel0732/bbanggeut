"""Real-aiortc check that mic audio reaches the STT pipeline (W01).

Everything below the Provider is genuine: a real ``RTCPeerConnection`` playing
"React" sends a real Audio Track, ``AiortcPeerConnection`` receives it, the
``SessionAudioBridge`` fans it out, and ``SessionSttPipeline`` converts and
buffers it. Only the recogniser is a Fake — §10 keeps real Providers out of the
default suite.

The unit tests cover the pipeline's logic; this one answers the question they
cannot: does audio actually arrive through WebRTC in a shape the converter
accepts?
"""

import asyncio
import logging
from datetime import datetime, timezone

import pytest
from aiortc import RTCConfiguration, RTCPeerConnection, RTCSessionDescription
from aiortc.mediastreams import AudioStreamTrack

from app.events.outbound import ConversationEventPublisher
from app.speech.recognizer import FakeSpeechRecognizer, SttResult
from app.speech.stt_pipeline import SessionSttPipeline
from app.speech.turn_state import TurnSequencer
from app.webrtc.data_channel import FakeDataChannel
from app.webrtc.peer_connection import AiortcPeerConnectionFactory

_LOGGER = logging.getLogger("test.webrtc.stt_audio_path")


async def _wait_until(predicate, timeout: float = 10.0, interval: float = 0.01) -> None:
    async def _poll() -> None:
        while not predicate():
            await asyncio.sleep(interval)

    await asyncio.wait_for(_poll(), timeout=timeout)


@pytest.mark.asyncio
async def test_microphone_audio_travels_through_webrtc_into_stt_final() -> None:
    client = RTCPeerConnection(RTCConfiguration(iceServers=[]))
    server = AiortcPeerConnectionFactory().create()
    channel = FakeDataChannel()
    recognizer = FakeSpeechRecognizer(
        [
            SttResult(
                text="오늘 과일 게임 했어.",
                audio_seconds=1.0,
                no_speech_prob=0.0,
                avg_logprob=-0.299,
                reported_end_seconds=1.0,
            )
        ]
    )
    pipeline = SessionSttPipeline(
        recognizer,
        ConversationEventPublisher(
            channel,
            conversation_id="conv_1",
            realtime_session_id="rt_1",
            session_epoch=1,
            logger=_LOGGER,
            clock=lambda: datetime(2026, 7, 27, tzinfo=timezone.utc),
        ),
        _LOGGER,
        turn_sequencer=TurnSequencer(turn_id_factory=lambda: "turn_1"),
    )
    try:
        client.addTrack(AudioStreamTrack())  # deterministic silence generator

        offer = await client.createOffer()
        await client.setLocalDescription(offer)
        answer_sdp = await server.accept_offer(client.localDescription.sdp)
        await client.setRemoteDescription(
            RTCSessionDescription(sdp=answer_sdp, type="answer")
        )
        # Attached after negotiation, mirroring how SignalingConnection does it
        # once the DataChannel arrives.
        server.attach_audio_sink(pipeline)

        await _wait_until(lambda: pipeline.has_audio, timeout=10.0)
        await _wait_until(
            lambda: pipeline._buffer.duration_seconds >= 0.6,  # noqa: SLF001
            timeout=10.0,
        )

        result = await pipeline.finalize_turn()

        assert result is not None
        assert channel.sent[0]["type"] == "stt.final"
        assert channel.sent[0]["turnId"] == "turn_1"
        # The Fake received a real WAV built from genuinely received frames.
        wav_size, audio_seconds = recognizer.calls[0]
        assert wav_size > 44
        assert audio_seconds >= 0.6
    finally:
        await asyncio.wait_for(pipeline.aclose(), timeout=10.0)
        await asyncio.wait_for(client.close(), timeout=10.0)
        await asyncio.wait_for(server.close(), timeout=10.0)


@pytest.mark.asyncio
async def test_closing_the_peer_connection_closes_the_stt_pipeline() -> None:
    """PeerConnection.close() already runs on all three cleanup paths (W04 §13),
    so hanging the sink off the bridge inherits them."""

    client = RTCPeerConnection(RTCConfiguration(iceServers=[]))
    server = AiortcPeerConnectionFactory().create()
    closed: list[int] = []

    class _Sink:
        def on_frame(self, frame) -> None:
            pass

        async def aclose(self) -> None:
            closed.append(1)

    try:
        client.addTrack(AudioStreamTrack())
        offer = await client.createOffer()
        await client.setLocalDescription(offer)
        answer_sdp = await server.accept_offer(client.localDescription.sdp)
        await client.setRemoteDescription(
            RTCSessionDescription(sdp=answer_sdp, type="answer")
        )
        server.attach_audio_sink(_Sink())

        await asyncio.wait_for(server.close(), timeout=10.0)

        assert closed == [1]
    finally:
        await asyncio.wait_for(client.close(), timeout=10.0)


@pytest.mark.asyncio
async def test_real_webrtc_silence_does_not_open_a_turn_with_the_real_vad() -> None:
    """W02 end-to-end negative check: a genuine aiortc silence track, judged by
    the real webrtcvad, never confirms speech — so nothing is buffered and the
    Provider is never called."""

    from app.speech.vad import WebrtcVoiceActivityDetector

    client = RTCPeerConnection(RTCConfiguration(iceServers=[]))
    server = AiortcPeerConnectionFactory().create()
    channel = FakeDataChannel()
    recognizer = FakeSpeechRecognizer()
    pipeline = SessionSttPipeline(
        recognizer,
        ConversationEventPublisher(
            channel,
            conversation_id="conv_1",
            realtime_session_id="rt_1",
            session_epoch=1,
            logger=_LOGGER,
            clock=lambda: datetime(2026, 7, 27, tzinfo=timezone.utc),
        ),
        _LOGGER,
        vad=WebrtcVoiceActivityDetector(),
    )
    try:
        client.addTrack(AudioStreamTrack())  # silence generator

        offer = await client.createOffer()
        await client.setLocalDescription(offer)
        answer_sdp = await server.accept_offer(client.localDescription.sdp)
        await client.setRemoteDescription(
            RTCSessionDescription(sdp=answer_sdp, type="answer")
        )
        server.attach_audio_sink(pipeline)

        await asyncio.sleep(1.0)  # 실제 무음 오디오 ~1초 수신

        assert not pipeline.has_audio
        assert recognizer.calls == []
        assert channel.sent == []
    finally:
        await asyncio.wait_for(pipeline.aclose(), timeout=10.0)
        await asyncio.wait_for(client.close(), timeout=10.0)
        await asyncio.wait_for(server.close(), timeout=10.0)
