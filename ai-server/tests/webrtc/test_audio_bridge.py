"""Tests for SessionAudioBridge — inbound consume + STT sink fan-out.

The echo track was removed in DIARY-3-W04 (replaced by the real AI voice), so
this bridge now only consumes the child's inbound audio and feeds the sink.
"""

import asyncio
import logging

import pytest
from aiortc.mediastreams import MediaStreamError, MediaStreamTrack
from av import AudioFrame

from app.webrtc.audio_bridge import SessionAudioBridge

_LOGGER = logging.getLogger("test.webrtc.audio_bridge")


def _make_frame(sample_rate: int = 48000, samples: int = 960) -> AudioFrame:
    frame = AudioFrame(format="s16", layout="mono", samples=samples)
    for plane in frame.planes:
        plane.update(bytes(plane.buffer_size))
    frame.sample_rate = sample_rate
    return frame


class FakeInboundAudioTrack(MediaStreamTrack):
    """Yields a fixed list of Frames, then raises MediaStreamError (track ended)."""

    kind = "audio"

    def __init__(self, frames: list[AudioFrame]) -> None:
        super().__init__()
        self._frames = list(frames)

    async def recv(self) -> AudioFrame:
        if not self._frames:
            raise MediaStreamError("no more frames")
        return self._frames.pop(0)


class BlockingInboundAudioTrack(MediaStreamTrack):
    """Never yields a frame — simulates "still connected, nothing to send yet"."""

    kind = "audio"

    async def recv(self) -> AudioFrame:
        await asyncio.Event().wait()
        raise AssertionError("unreachable")  # pragma: no cover


class RecordingSink:
    """Fake AudioSink capturing the fan-out."""

    def __init__(self, fail: bool = False) -> None:
        self.frames: list[AudioFrame] = []
        self.closed = 0
        self._fail = fail

    def on_frame(self, frame: AudioFrame) -> None:
        if self._fail:
            raise RuntimeError("sink is broken")
        self.frames.append(frame)

    async def aclose(self) -> None:
        self.closed += 1


@pytest.mark.asyncio
async def test_inbound_frames_reach_the_sink() -> None:
    sink = RecordingSink()
    bridge = SessionAudioBridge(_LOGGER, sink=sink)
    bridge.start(FakeInboundAudioTrack([_make_frame(), _make_frame()]))

    for _ in range(5):
        await asyncio.sleep(0)  # let the consume task drain the fake track

    assert len(sink.frames) == 2
    await bridge.stop()


@pytest.mark.asyncio
async def test_start_twice_raises() -> None:
    bridge = SessionAudioBridge(_LOGGER)
    bridge.start(FakeInboundAudioTrack([]))

    with pytest.raises(RuntimeError):
        bridge.start(FakeInboundAudioTrack([]))

    await bridge.stop()


@pytest.mark.asyncio
async def test_stop_cancels_a_still_blocked_consume_task() -> None:
    bridge = SessionAudioBridge(_LOGGER)
    bridge.start(BlockingInboundAudioTrack())
    await asyncio.sleep(0)  # let the consume task actually start awaiting recv()

    await asyncio.wait_for(bridge.stop(), timeout=1)

    assert not bridge.is_running


@pytest.mark.asyncio
async def test_stop_is_idempotent() -> None:
    bridge = SessionAudioBridge(_LOGGER)
    bridge.start(FakeInboundAudioTrack([]))

    await bridge.stop()
    await bridge.stop()  # second call must not raise

    assert not bridge.is_running


@pytest.mark.asyncio
async def test_inbound_track_ending_naturally_does_not_crash_the_consume_task() -> None:
    bridge = SessionAudioBridge(_LOGGER)
    bridge.start(FakeInboundAudioTrack([_make_frame()]))

    for _ in range(5):
        await asyncio.sleep(0)  # consume task observes MediaStreamError and exits cleanly

    await bridge.stop()  # must still be safe to call


@pytest.mark.asyncio
async def test_no_sink_bridge_still_consumes_without_error() -> None:
    bridge = SessionAudioBridge(_LOGGER)
    bridge.start(FakeInboundAudioTrack([_make_frame()]))

    for _ in range(5):
        await asyncio.sleep(0)

    assert bridge.has_sink is False
    await bridge.stop()


@pytest.mark.asyncio
async def test_stop_closes_the_attached_sink() -> None:
    sink = RecordingSink()
    bridge = SessionAudioBridge(_LOGGER, sink=sink)
    bridge.start(BlockingInboundAudioTrack())

    await bridge.stop()

    assert sink.closed == 1


@pytest.mark.asyncio
async def test_stop_closes_the_sink_only_once() -> None:
    sink = RecordingSink()
    bridge = SessionAudioBridge(_LOGGER, sink=sink)
    bridge.start(BlockingInboundAudioTrack())

    await bridge.stop()
    await bridge.stop()

    assert sink.closed == 1


@pytest.mark.asyncio
async def test_stop_closes_the_sink_even_if_the_bridge_never_started() -> None:
    sink = RecordingSink()
    bridge = SessionAudioBridge(_LOGGER, sink=sink)

    await bridge.stop()

    assert sink.closed == 1


@pytest.mark.asyncio
async def test_a_failing_sink_does_not_kill_the_media_loop() -> None:
    """Losing STT is recoverable; losing the receive loop drops the call."""

    bridge = SessionAudioBridge(_LOGGER, sink=RecordingSink(fail=True))
    bridge.start(FakeInboundAudioTrack([_make_frame(), _make_frame()]))

    for _ in range(5):
        await asyncio.sleep(0)  # consume task survives the raising sink

    assert bridge.is_running
    await bridge.stop()


@pytest.mark.asyncio
async def test_sink_can_be_attached_after_construction() -> None:
    """aiortc raises "track" before the DataChannel exists, so the sink -
    which publishes through that channel - is attached later."""

    bridge = SessionAudioBridge(_LOGGER)
    bridge.start(BlockingInboundAudioTrack())
    sink = RecordingSink()

    bridge.attach_sink(sink)

    assert bridge.has_sink is True
    await bridge.stop()
    assert sink.closed == 1


@pytest.mark.asyncio
async def test_attaching_two_sinks_raises() -> None:
    bridge = SessionAudioBridge(_LOGGER, sink=RecordingSink())

    with pytest.raises(RuntimeError):
        bridge.attach_sink(RecordingSink())
