"""Per-session inbound Audio Frame consumer, Echo Track, and STT fan-out.

W04 built this to prove the Audio Track pipe works end to end (child mic Track
-> FastAPI -> Echo Track back). DIARY-3-W01 adds the real consumer: an optional
``AudioSink`` that receives every inbound Frame alongside the echo queue.

The echo path stays. W06's Harness verifies real microphone round-trip through
it, so removing it here would break that check; the outbound track is replaced
for real when TTS arrives in DIARY-3-W04 (jira/plan/DIARY-3-W01.md §3-4).
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
from fractions import Fraction
from typing import Protocol

from aiortc.mediastreams import MediaStreamError, MediaStreamTrack
from av import AudioFrame

_DEFAULT_MAX_QUEUE_SIZE = 50  # ~1s at 20ms/frame — backpressure bound (§3-2)


class AudioSink(Protocol):
    """Consumer of one session's inbound Frames (the STT pipeline in practice)."""

    def on_frame(self, frame: AudioFrame) -> None:
        """Must not block: this runs inside the media receive loop."""
        ...

    async def aclose(self) -> None:
        """Release the sink's own tasks and buffers. Idempotent."""
        ...


class EchoAudioTrack(MediaStreamTrack):
    """Re-emits Frames pulled from a SessionAudioBridge's queue.

    Each Frame is re-timestamped onto this track's own outbound timeline —
    the original inbound pts sequence is not reused, since this track starts
    its own independent RTP timeline once added via ``addTrack()``.
    """

    kind = "audio"

    def __init__(self, queue: "asyncio.Queue[AudioFrame]") -> None:
        super().__init__()
        self._queue = queue
        self._next_pts = 0

    async def recv(self) -> AudioFrame:
        if self.readyState != "live":
            raise MediaStreamError("track is not live")
        frame = await self._queue.get()
        frame.pts = self._next_pts
        frame.time_base = Fraction(1, frame.sample_rate)
        self._next_pts += frame.samples
        return frame


class SessionAudioBridge:
    """Owns one session's inbound Audio Consumer Task and Echo Queue.

    One instance per Signaling connection — never shared across sessions
    (AI_CONVENTIONS.md §8 session isolation), mirroring the "one instance per
    connection" rule already established for ``SessionLeaseHeartbeat``.
    """

    def __init__(
        self,
        logger: logging.Logger,
        max_queue_size: int = _DEFAULT_MAX_QUEUE_SIZE,
        sink: AudioSink | None = None,
    ) -> None:
        self._logger = logger
        self._queue: asyncio.Queue[AudioFrame] = asyncio.Queue(maxsize=max_queue_size)
        self._consume_task: asyncio.Task | None = None
        self._sink = sink

    @property
    def is_running(self) -> bool:
        return self._consume_task is not None

    @property
    def has_sink(self) -> bool:
        return self._sink is not None

    def attach_sink(self, sink: AudioSink) -> None:
        """Attach the STT sink after the fact.

        Required by the real event order: aiortc raises ``track`` during
        ``setRemoteDescription()``, but the ``conversation-events`` DataChannel
        only arrives once SCTP finishes — and the sink needs that channel to
        publish ``stt.final``. Frames received before this point are echoed but
        not transcribed, which is harmless because the client only starts
        speaking after ``client.ready`` (REALTIME_EVENT_CONTRACT.md §6.1).
        """

        if self._sink is not None:
            raise RuntimeError("audio sink already attached")
        self._sink = sink

    def start(self, inbound_track: MediaStreamTrack) -> None:
        if self._consume_task is not None:
            raise RuntimeError("audio bridge already started")
        self._consume_task = asyncio.create_task(self._consume(inbound_track))

    def create_outbound_track(self) -> EchoAudioTrack:
        return EchoAudioTrack(self._queue)

    async def _consume(self, inbound_track: MediaStreamTrack) -> None:
        try:
            while True:
                frame = await inbound_track.recv()
                self._feed_sink(frame)
                if self._queue.full():
                    self._queue.get_nowait()  # drop-oldest backpressure(§3-2)
                self._queue.put_nowait(frame)
        except asyncio.CancelledError:
            raise
        except MediaStreamError:
            self._logger.info("inbound audio track ended")

    def _feed_sink(self, frame: AudioFrame) -> None:
        if self._sink is None:
            return
        try:
            self._sink.on_frame(frame)
        except Exception:  # noqa: BLE001 - a broken sink must not kill the media loop
            # Losing STT is recoverable; losing the receive loop drops the call.
            self._logger.exception("audio sink rejected a frame")

    async def stop(self) -> None:
        """Stop the consumer and the sink. Idempotent."""

        task = self._consume_task
        self._consume_task = None
        if task is not None:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task
        sink, self._sink = self._sink, None
        if sink is not None:
            await sink.aclose()
