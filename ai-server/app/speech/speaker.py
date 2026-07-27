"""Outbound AI audio: per-response queue and the paced speaker track (T17/T18).

Replaces the W02-era echo track. The child now hears the synthesized AI voice
on the single outbound WebRTC audio track, isolated per ``responseId`` so that
cancelling one response (barge-in, DIARY-4) can never bleed into the next.

Two aiortc facts (confirmed against source, W04 plan §1-B) shape this:

- The Opus encoder resamples internally — any s16 mono/stereo frame at any rate
  is accepted — so PCM is only cut into 20ms frames here, never resampled.
- The sender pulls ``recv()`` in a tight loop, so the track itself must pace:
  it sleeps to hold a 20ms cadence and emits silence when nothing is queued,
  keeping RTP flowing and making a flush produce instant silence.
"""

from __future__ import annotations

import asyncio
import time
from fractions import Fraction

from aiortc.mediastreams import MediaStreamError, MediaStreamTrack
from av import AudioFrame

from app.speech.audio_format import TTS_OUTPUT_FORMAT, AudioFormat

_FRAME_MILLISECONDS = 20


def _frame_samples(audio_format: AudioFormat) -> int:
    return audio_format.sample_rate * _FRAME_MILLISECONDS // 1000


class ResponseAudioQueue:
    """One ``responseId``'s frames awaiting playback.

    Bounded so a burst of synthesis cannot grow memory without limit; ``put``
    waits when full (backpressure onto the synthesis task). ``flush`` drops
    everything buffered and marks the queue closed, so a late chunk arriving
    after a cancel is discarded rather than played (ADR-012).
    """

    def __init__(self, response_id: str, max_frames: int) -> None:
        self.response_id = response_id
        self._queue: asyncio.Queue[AudioFrame] = asyncio.Queue(maxsize=max_frames)
        self._finished = False
        self._flushed = False

    async def put(self, frame: AudioFrame) -> None:
        if self._flushed:
            return  # cancelled — silently drop late audio
        await self._queue.put(frame)

    def get_nowait(self) -> AudioFrame | None:
        try:
            return self._queue.get_nowait()
        except asyncio.QueueEmpty:
            return None

    def finish(self) -> None:
        """Mark that no more frames will be produced (synthesis completed)."""

        self._finished = True

    def flush(self) -> None:
        """Drop buffered frames and refuse further puts. Idempotent."""

        self._flushed = True
        self._finished = True
        while self.get_nowait() is not None:
            pass

    @property
    def is_flushed(self) -> bool:
        return self._flushed

    @property
    def drained(self) -> bool:
        """True once producing has finished and every frame has been consumed."""

        return self._finished and self._queue.empty()


class SpeakerAudioTrack(MediaStreamTrack):
    """Paced outbound track: current response's frames, else silence.

    pts advances on this track's own 24kHz timeline regardless of what is
    played, so switching responses or flushing never disturbs the RTP clock.
    """

    kind = "audio"

    def __init__(self, audio_format: AudioFormat = TTS_OUTPUT_FORMAT) -> None:
        super().__init__()
        self._audio_format = audio_format
        self._samples = _frame_samples(audio_format)
        self._time_base = Fraction(1, audio_format.sample_rate)
        self._next_pts = 0
        self._current: ResponseAudioQueue | None = None
        self._start: float | None = None

    def set_current(self, queue: ResponseAudioQueue | None) -> None:
        self._current = queue

    async def recv(self) -> AudioFrame:
        if self.readyState != "live":
            raise MediaStreamError("track is not live")
        await self._pace()
        frame = self._next_frame()
        frame.pts = self._next_pts
        frame.time_base = self._time_base
        frame.sample_rate = self._audio_format.sample_rate
        self._next_pts += self._samples
        return frame

    async def _pace(self) -> None:
        now = time.monotonic()
        if self._start is None:
            self._start = now
            return
        target = self._start + (self._next_pts / self._audio_format.sample_rate)
        wait = target - now
        if wait > 0:
            await asyncio.sleep(wait)

    def _next_frame(self) -> AudioFrame:
        if self._current is not None:
            frame = self._current.get_nowait()
            if frame is not None:
                return frame
        return self._silence()

    def _silence(self) -> AudioFrame:
        frame = AudioFrame(
            format="s16",
            layout="mono" if self._audio_format.channels == 1 else "stereo",
            samples=self._samples,
        )
        for plane in frame.planes:
            plane.update(bytes(plane.buffer_size))
        return frame


class SpeakerHandle:
    """Narrow interface the session uses — never the track itself.

    Owns the mapping of response -> queue and swaps the track's current queue
    at response boundaries.
    """

    def __init__(self, track: SpeakerAudioTrack, max_queue_frames: int) -> None:
        self._track = track
        self._max_queue_frames = max_queue_frames
        self._current: ResponseAudioQueue | None = None

    def begin_response(self, response_id: str) -> ResponseAudioQueue:
        """Start a new response's playback, flushing any previous one."""

        if self._current is not None:
            self._current.flush()
        queue = ResponseAudioQueue(response_id, self._max_queue_frames)
        self._current = queue
        self._track.set_current(queue)
        return queue

    def cancel_response(self, response_id: str) -> None:
        """Flush the given response if it is the one currently playing."""

        if self._current is not None and self._current.response_id == response_id:
            self._current.flush()


class PcmFramer:
    """Cuts a PCM byte stream into fixed 20ms frames, carrying the remainder.

    Mirrors ``VadFramer`` (W02): the TTS stream's chunk boundaries do not align
    to frame size, so leftover bytes are held for the next chunk. ``flush``
    pads the final partial frame with silence rather than dropping it.
    """

    def __init__(self, audio_format: AudioFormat = TTS_OUTPUT_FORMAT) -> None:
        self._audio_format = audio_format
        self._samples = _frame_samples(audio_format)
        self._frame_bytes = self._samples * audio_format.bytes_per_sample
        self._time_base = Fraction(1, audio_format.sample_rate)
        self._remainder = b""

    def push(self, data: bytes) -> list[AudioFrame]:
        buffer = self._remainder + data
        frames = []
        offset = 0
        while offset + self._frame_bytes <= len(buffer):
            frames.append(self._build(buffer[offset : offset + self._frame_bytes]))
            offset += self._frame_bytes
        self._remainder = buffer[offset:]
        return frames

    def flush(self) -> list[AudioFrame]:
        if not self._remainder:
            return []
        padded = self._remainder + bytes(self._frame_bytes - len(self._remainder))
        self._remainder = b""
        return [self._build(padded)]

    def _build(self, frame_bytes: bytes) -> AudioFrame:
        frame = AudioFrame(
            format="s16",
            layout="mono" if self._audio_format.channels == 1 else "stereo",
            samples=self._samples,
        )
        frame.sample_rate = self._audio_format.sample_rate
        frame.time_base = self._time_base
        frame.planes[0].update(frame_bytes)
        return frame
