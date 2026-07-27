"""Tests for SessionAudioBridge/EchoAudioTrack (T13)."""

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


@pytest.mark.asyncio
async def test_outbound_track_replays_frames_with_own_pts_timeline() -> None:
    frames = [_make_frame(samples=960), _make_frame(samples=960)]
    bridge = SessionAudioBridge(_LOGGER)
    bridge.start(FakeInboundAudioTrack(frames))
    outbound = bridge.create_outbound_track()

    first = await outbound.recv()
    second = await outbound.recv()

    assert first.pts == 0
    assert second.pts == 960
    assert first.sample_rate == 48000
    await bridge.stop()


@pytest.mark.asyncio
async def test_backpressure_drops_oldest_frames_when_queue_full() -> None:
    frames = [_make_frame() for _ in range(5)]
    bridge = SessionAudioBridge(_LOGGER, max_queue_size=2)
    bridge.start(FakeInboundAudioTrack(frames))
    await asyncio.sleep(0)  # let the consume task drain the fake (non-pacing) track

    outbound = bridge.create_outbound_track()
    first = await outbound.recv()
    second = await outbound.recv()

    # Only the last 2 of 5 frames survive backpressure (drop-oldest).
    assert first is frames[3]
    assert second is frames[4]
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
    outbound = bridge.create_outbound_track()

    await outbound.recv()
    await asyncio.sleep(0)  # let the consume task observe MediaStreamError and exit cleanly

    await bridge.stop()  # must still be safe to call


class RecordingSink:
    """Fake AudioSink capturing the fan-out (W01/T02-b)."""

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
async def test_echo_path_still_works_when_a_sink_is_attached() -> None:
    """W06's Harness verifies real microphone echo — W01 must not break it."""

    sink = RecordingSink()
    bridge = SessionAudioBridge(_LOGGER, sink=sink)
    bridge.start(FakeInboundAudioTrack([_make_frame(), _make_frame()]))
    outbound = bridge.create_outbound_track()

    first = await outbound.recv()
    second = await outbound.recv()

    assert first.pts < second.pts
    assert len(sink.frames) == 2
    await bridge.stop()


@pytest.mark.asyncio
async def test_bridge_without_a_sink_behaves_exactly_as_before() -> None:
    bridge = SessionAudioBridge(_LOGGER)
    bridge.start(FakeInboundAudioTrack([_make_frame()]))
    outbound = bridge.create_outbound_track()

    assert await outbound.recv() is not None
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
    """Cleanup runs on connections that failed before any audio arrived."""

    sink = RecordingSink()
    bridge = SessionAudioBridge(_LOGGER, sink=sink)

    await bridge.stop()

    assert sink.closed == 1


@pytest.mark.asyncio
async def test_a_failing_sink_does_not_kill_the_media_loop() -> None:
    """Losing STT is recoverable; losing the receive loop drops the call."""

    bridge = SessionAudioBridge(_LOGGER, sink=RecordingSink(fail=True))
    bridge.start(FakeInboundAudioTrack([_make_frame(), _make_frame()]))
    outbound = bridge.create_outbound_track()

    assert await outbound.recv() is not None
    assert await outbound.recv() is not None
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
