"""Response queue, PCM framing, and paced speaker track tests (T17/T18)."""

import asyncio

import pytest
from av import AudioFrame

from app.speech.speaker import (
    PcmFramer,
    ResponseAudioQueue,
    SpeakerAudioTrack,
    SpeakerHandle,
)
from app.speech.tts_client import TTS_OUTPUT_FORMAT

_FRAME_BYTES = TTS_OUTPUT_FORMAT.sample_rate * 20 // 1000 * 2  # 480 samples * 2 = 960


def _frame() -> AudioFrame:
    frame = AudioFrame(format="s16", layout="mono", samples=480)
    frame.sample_rate = TTS_OUTPUT_FORMAT.sample_rate
    for plane in frame.planes:
        plane.update(bytes(plane.buffer_size))
    return frame


class TestResponseAudioQueue:
    @pytest.mark.asyncio
    async def test_put_and_get(self) -> None:
        queue = ResponseAudioQueue("resp_1", max_frames=10)
        await queue.put(_frame())

        assert queue.get_nowait() is not None
        assert queue.get_nowait() is None

    @pytest.mark.asyncio
    async def test_flush_drops_buffered_and_refuses_late_puts(self) -> None:
        queue = ResponseAudioQueue("resp_1", max_frames=10)
        await queue.put(_frame())

        queue.flush()

        assert queue.get_nowait() is None  # dropped
        await queue.put(_frame())  # late chunk after cancel
        assert queue.get_nowait() is None  # silently ignored
        assert queue.is_flushed

    @pytest.mark.asyncio
    async def test_flush_is_idempotent(self) -> None:
        queue = ResponseAudioQueue("resp_1", max_frames=10)
        queue.flush()
        queue.flush()

        assert queue.is_flushed

    @pytest.mark.asyncio
    async def test_drained_only_after_finish_and_empty(self) -> None:
        queue = ResponseAudioQueue("resp_1", max_frames=10)
        await queue.put(_frame())
        assert not queue.drained  # not finished

        queue.finish()
        assert not queue.drained  # finished but a frame remains

        queue.get_nowait()
        assert queue.drained

    @pytest.mark.asyncio
    async def test_bounded_put_blocks_when_full(self) -> None:
        queue = ResponseAudioQueue("resp_1", max_frames=1)
        await queue.put(_frame())

        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(queue.put(_frame()), timeout=0.05)


class TestPcmFramer:
    def test_cuts_into_fixed_frames_carrying_remainder(self) -> None:
        framer = PcmFramer(TTS_OUTPUT_FORMAT)

        # 1.5 frames of audio → 1 frame out, half carried
        frames = framer.push(bytes(_FRAME_BYTES + _FRAME_BYTES // 2))
        assert len(frames) == 1
        assert frames[0].samples == 480

        # another half completes the second frame
        frames = framer.push(bytes(_FRAME_BYTES // 2))
        assert len(frames) == 1

    def test_flush_pads_the_final_partial_frame(self) -> None:
        framer = PcmFramer(TTS_OUTPUT_FORMAT)
        framer.push(bytes(100))  # under one frame

        frames = framer.flush()

        assert len(frames) == 1
        assert frames[0].samples == 480  # padded to a full frame

    def test_flush_with_no_remainder_yields_nothing(self) -> None:
        framer = PcmFramer(TTS_OUTPUT_FORMAT)
        framer.push(bytes(_FRAME_BYTES))

        assert framer.flush() == []

    def test_audible_bytes_survive_framing(self) -> None:
        framer = PcmFramer(TTS_OUTPUT_FORMAT)

        frames = framer.push(b"\x11\x22" * 480)

        assert any(bytes(frames[0].planes[0]))


class TestSpeakerTrack:
    @pytest.mark.asyncio
    async def test_emits_silence_when_nothing_queued(self) -> None:
        track = SpeakerAudioTrack()

        frame = await track.recv()

        assert frame.samples == 480
        assert not any(bytes(frame.planes[0]))

    @pytest.mark.asyncio
    async def test_pts_increases_monotonically(self) -> None:
        track = SpeakerAudioTrack()

        first = await track.recv()
        second = await track.recv()

        assert second.pts > first.pts
        assert second.pts - first.pts == 480

    @pytest.mark.asyncio
    async def test_plays_queued_audio_then_returns_to_silence(self) -> None:
        track = SpeakerAudioTrack()
        queue = ResponseAudioQueue("resp_1", max_frames=10)
        audible = AudioFrame(format="s16", layout="mono", samples=480)
        audible.sample_rate = TTS_OUTPUT_FORMAT.sample_rate
        audible.planes[0].update(b"\x11\x22" * 480)
        await queue.put(audible)
        track.set_current(queue)

        played = await track.recv()
        silence = await track.recv()

        assert any(bytes(played.planes[0]))
        assert not any(bytes(silence.planes[0]))


class TestSpeakerHandle:
    @pytest.mark.asyncio
    async def test_flush_cannot_leak_a_blocked_late_frame(self) -> None:
        track = SpeakerAudioTrack()
        queue = ResponseAudioQueue("resp_a", max_frames=1)
        track.set_current(queue)
        await queue.put(_frame())
        blocked_put = asyncio.create_task(queue.put(_frame()))
        await asyncio.sleep(0)
        assert not blocked_put.done()

        queue.flush()
        await blocked_put
        frame = await track.recv()

        assert queue.is_flushed
        assert not any(bytes(frame.planes[0]))

    @pytest.mark.asyncio
    async def test_begin_response_flushes_the_previous_one(self) -> None:
        """Two responseId must not mix: starting B flushes A (barge-in path)."""

        track = SpeakerAudioTrack()
        handle = SpeakerHandle(track, max_queue_frames=10)

        queue_a = handle.begin_response("resp_a")
        await queue_a.put(_frame())

        queue_b = handle.begin_response("resp_b")

        assert queue_a.is_flushed
        assert queue_a.get_nowait() is None
        assert not queue_b.is_flushed
        assert queue_b.response_id == "resp_b"

    @pytest.mark.asyncio
    async def test_cancel_flushes_only_the_matching_response(self) -> None:
        track = SpeakerAudioTrack()
        handle = SpeakerHandle(track, max_queue_frames=10)
        queue = handle.begin_response("resp_a")

        handle.cancel_response("resp_other")
        assert not queue.is_flushed

        handle.cancel_response("resp_a")
        assert queue.is_flushed
